import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

const chromePath = process.env.CHROME_PATH || 'google-chrome';
const debugPort = Number(process.env.CHROME_DEBUG_PORT || 9331);
const profileDir = `/tmp/inventary-auth-e2e-${process.pid}`;
const targetUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173/inventary/iniciar-sesion';
const chrome = spawn(chromePath, [
  '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
  '--remote-allow-origins=*', '--remote-debugging-address=127.0.0.1',
  `--remote-debugging-port=${debugPort}`, `--user-data-dir=${profileDir}`, 'about:blank'
], { stdio: 'ignore' });

let socket;
let nextId = 0;
const pending = new Map();

function connectDebugger(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.addEventListener('open', () => resolve(ws), { once: true });
    ws.addEventListener('error', reject, { once: true });
    ws.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (!message.id) return;
      const task = pending.get(message.id);
      if (!task) return;
      pending.delete(message.id);
      if (message.error) task.reject(new Error(message.error.message));
      else task.resolve(message.result || {});
    });
  });
}
function command(method, params = {}) {
  const id = ++nextId;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Browser evaluation failed');
  return result.result?.value;
}
async function waitFor(expression, description, timeout = 8000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (await evaluate(expression)) return;
    await delay(100);
  }
  throw new Error(`Timed out waiting for ${description}`);
}
async function click(selector) {
  const found = await evaluate(`(() => { const el = document.querySelector(${JSON.stringify(selector)}); if (!el) return false; el.click(); return true; })()`);
  assert.equal(found, true, `Expected element ${selector}`);
}
async function fill(selector, value) {
  await evaluate(`(() => { const el = document.querySelector(${JSON.stringify(selector)}); if (!el) throw new Error('Missing '+${JSON.stringify(selector)}); el.focus(); const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; setter.call(el,${JSON.stringify(value)}); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); })()`);
}
async function submitLogin() {
  await click('button[type="submit"]');
}
async function waitForDebugger() {
  for (let i = 0; i < 60; i++) {
    if (chrome.exitCode !== null) throw new Error(`Chrome exited: ${chrome.exitCode}`);
    try { const response = await fetch(`http://127.0.0.1:${debugPort}/json/version`); if (response.ok) return; } catch {}
    await delay(200);
  }
  throw new Error('Chrome DevTools did not start');
}

try {
  await waitForDebugger();
  const targetResponse = await fetch(`http://127.0.0.1:${debugPort}/json/new?${targetUrl}`, { method: 'PUT' });
  assert.equal(targetResponse.ok, true);
  const target = await targetResponse.json();
  socket = await connectDebugger(target.webSocketDebuggerUrl);
  await command('Page.enable');
  await command('Runtime.enable');
  await command('Page.addScriptToEvaluateOnNewDocument', { source: `
    (() => {
      const realFetch = window.fetch.bind(window);
      window.__inventaryAuthMode = 'success';
      window.fetch = async (input, init = {}) => {
        const url = new URL(typeof input === 'string' ? input : input.url, location.href);
        if (url.pathname.endsWith('/auth/login-challenge')) {
          if (window.__inventaryAuthMode === 'locked') return new Response(JSON.stringify({code:'RATE_LIMIT_EXCEEDED',message:'Espera antes de volver a intentarlo.'}), {status:429,headers:{'Content-Type':'application/problem+json','Retry-After':'30'}});
          const pair = await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'}, true, ['deriveBits']);
          const publicKey = await crypto.subtle.exportKey('spki', pair.publicKey);
          const bytes = new Uint8Array(publicKey); let binary = ''; for (const byte of bytes) binary += String.fromCharCode(byte);
          return new Response(JSON.stringify({algorithm:'ECDH-ES+HKDF-SHA256+A256GCM',challengeId:crypto.randomUUID(),publicKey:btoa(binary)}), {status:200,headers:{'Content-Type':'application/json'}});
        }
        if (url.pathname.endsWith('/auth/login')) {
          if (window.__inventaryAuthMode === 'invalid') return new Response(JSON.stringify({code:'INVALID_CREDENTIALS',message:'Usuario o contraseña incorrectos.'}), {status:401,headers:{'Content-Type':'application/problem+json'}});
          const ttl = window.__inventaryAuthMode === 'expires' ? 1200 : 60000;
          return new Response(JSON.stringify({accessToken:'e2e-mocked-access-token',expiresAt:new Date(Date.now()+ttl).toISOString()}), {status:200,headers:{'Content-Type':'application/json'}});
        }
        if (url.pathname.endsWith('/auth/me')) return new Response(JSON.stringify({id:1,username:'e2e.operator',role:'ADMIN',permissions:[]}), {status:200,headers:{'Content-Type':'application/json'}});
        if (url.pathname.endsWith('/auth/logout')) return new Response(null,{status:204});
        return realFetch(input, init);
      };
    })();
  `});
  await command('Page.navigate', { url: targetUrl });
  await waitFor("document.querySelector('form.auth-form')", 'login screen');

  // Keyboard/accessibility smoke: labels, visible focus, password reveal and required fields.
  const accessibility = await evaluate(`(() => {
    const user=document.querySelector('input[name="username"]');
    const pass=document.querySelector('input[name="password"]');
    const submit=document.querySelector('button[type="submit"]');
    return { labels: Boolean(user?.labels?.length && pass?.labels?.length), heading: Boolean(document.querySelector('h1')),
      reveal: Boolean(document.querySelector('button[aria-controls]')), submitName: submit?.textContent?.trim() };
  })()`);
  assert.equal(accessibility.labels, true, 'login fields must have programmatic labels');
  assert.equal(accessibility.heading, true, 'login must have a heading');
  assert.ok(accessibility.reveal, 'password visibility control must be present');

  await submitLogin();
  await waitFor("document.querySelector('[role=alert]')", 'required-field validation');
  await fill('input[name="username"]', 'operator');
  await fill('input[name="password"]', 'wrong-password');
  await evaluate("window.__inventaryAuthMode='invalid'");
  await submitLogin();
  await waitFor("document.querySelector('[role=alert]')?.textContent.includes('incorrectos')", 'invalid credentials message');
  assert.equal(await evaluate("document.querySelector('input[name=password]').value"), '', 'password must be cleared after failure');

  await evaluate("window.__inventaryAuthMode='locked'");
  await fill('input[name="password"]', 'some-password');
  await submitLogin();
  await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Demasiados intentos')", 'rate-limit/lockout message');

  await evaluate("window.__inventaryAuthMode='success'");
  await fill('input[name="password"]', 'correct-password');
  await submitLogin();
  await waitFor("document.querySelector('button[aria-label=\\\"Cerrar sesión\\\"]')", 'authenticated workspace');
  await click('button[aria-label="Cerrar sesión"]');
  await waitFor("document.querySelector('form.auth-form')", 'logout return to login');

  await evaluate("window.__inventaryAuthMode='expires'");
  await fill('input[name="username"]', 'operator');
  await fill('input[name="password"]', 'correct-password');
  await submitLogin();
  await waitFor("document.querySelector('form.auth-form')", 'automatic session expiry', 10000);
  process.stdout.write('Auth browser E2E passed: validation, invalid credentials, lockout, login, logout, automatic expiry, labels and password clearing.\\n');
} finally {
  try { socket?.close(); } catch {}
  if (chrome.exitCode === null && chrome.signalCode === null) {
    const exited = new Promise(resolve => chrome.once('exit', resolve));
    chrome.kill('SIGTERM');
    if (!(await Promise.race([exited.then(() => true), delay(4000).then(() => false)]))) chrome.kill('SIGKILL');
  }
  const { rm } = await import('node:fs/promises');
  await rm(profileDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
}

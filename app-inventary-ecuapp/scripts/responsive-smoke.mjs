import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';

const chromePath = process.env.CHROME_PATH || 'google-chrome';
const debugPort = 9222;
const profileDir = `/tmp/inventary-chrome-smoke-${process.pid}`;
const targetUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:4173/inventary/iniciar-sesion';
const outputDir = new URL('../test-results/', import.meta.url);

await rm(profileDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
await rm(outputDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
await mkdir(outputDir, { recursive: true });

const chrome = spawn(chromePath, [
  '--headless=new',
  '--no-sandbox',
  '--disable-gpu',
  '--disable-dev-shm-usage',
  '--remote-allow-origins=*',
  `--remote-debugging-address=127.0.0.1`,
  `--remote-debugging-port=${debugPort}`,
  `--user-data-dir=${profileDir}`,
  'about:blank'
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
      let message;
      try { message = JSON.parse(event.data); } catch { return; }
      if (!message.id) return;
      const task = pending.get(message.id);
      if (!task) return;
      pending.delete(message.id);
      if (message.error) task.reject(new Error(message.error.message || 'Chrome DevTools command failed'));
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

async function waitForDebugger() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    if (chrome.exitCode !== null) throw new Error(`Chrome exited with code ${chrome.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${debugPort}/json/version`);
      if (response.ok) return;
    } catch { /* Chrome is still starting. */ }
    await delay(250);
  }
  throw new Error('Chrome DevTools did not become ready.');
}

async function evaluate(expression) {
  const result = await command('Runtime.evaluate', { expression, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Browser evaluation failed');
  return result.result?.value;
}

async function waitForLogin() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const state = await evaluate(`({
      ready: document.readyState === 'complete',
      form: Boolean(document.querySelector('form.auth-form')),
      title: document.title
    })`);
    if (state?.ready && state.form) return state;
    await delay(250);
  }
  throw new Error('The login screen did not render in the browser.');
}

try {
  await waitForDebugger();
  const targetResponse = await fetch(
    `http://127.0.0.1:${debugPort}/json/new?${targetUrl}`,
    { method: 'PUT' }
  );
  assert.equal(targetResponse.ok, true, 'Chrome must create the application page target');
  const target = await targetResponse.json();
  socket = await connectDebugger(target.webSocketDebuggerUrl);
  await command('Page.enable');
  await command('Runtime.enable');

  for (const width of [360, 768, 1280]) {
    await command('Emulation.setDeviceMetricsOverride', {
      width, height: 900, deviceScaleFactor: 1, mobile: width <= 480
    });
    await command('Page.navigate', { url: targetUrl });
    await waitForLogin();

    const result = await evaluate(`(() => {
      const inputs = Array.from(document.querySelectorAll('.auth-form input:not([type="checkbox"])'));
      const labelsBound = inputs.every(input =>
        Array.from(document.querySelectorAll('label[for]')).some(label => label.htmlFor === input.id)
      );
      const showcase = document.querySelector('.auth-showcase');
      return {
        title: document.title,
        width: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth,
        hasHeading: Boolean(document.querySelector('.auth-panel h1')),
        inputCount: inputs.length,
        labelsBound,
        showcaseDisplay: showcase ? getComputedStyle(showcase).display : 'missing'
      };
    })()`);

    assert.equal(result.width, width, `viewport should be ${width}px wide`);
    assert.ok(result.documentWidth <= width + 1, `horizontal document overflow at ${width}px: ${result.documentWidth}px`);
    assert.ok(result.bodyWidth <= width + 1, `horizontal body overflow at ${width}px: ${result.bodyWidth}px`);
    assert.match(result.title, /Inventary/i);
    assert.equal(result.hasHeading, true, 'login heading must render');
    assert.equal(result.inputCount, 2, 'login must expose username and password fields');
    assert.equal(result.labelsBound, true, 'username and password inputs must have programmatic labels');
    assert.equal(result.showcaseDisplay === 'none', width <= 820, 'showcase breakpoint must match the responsive layout');

    const screenshot = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(new URL(`responsive-${width}.png`, outputDir), Buffer.from(screenshot.data, 'base64'));
    process.stdout.write(`Responsive browser smoke passed at ${width}px: ${JSON.stringify(result)}\\n`);
  }
} finally {
  try { socket?.close(); } catch { /* Best-effort teardown. */ }
  // Chromium may still be flushing its profile after SIGTERM; wait for exit before removing it.
  if (chrome.exitCode === null && chrome.signalCode === null) {
    const exited = new Promise(resolve => chrome.once('exit', resolve));
    chrome.kill('SIGTERM');
    const stopped = await Promise.race([exited.then(() => true), delay(5000).then(() => false)]);
    if (!stopped) {
      chrome.kill('SIGKILL');
      await Promise.race([exited, delay(2000)]);
    }
  }
  await rm(profileDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
}

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getToken, getTokenExpiry, request, setToken } from './request.js';

test('mantiene la sesión durante la pestaña si el navegador bloquea todo almacenamiento', () => {
  const originalSession = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalLocal = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  try {
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    setToken('volatile-access-token', { expiresAt });
    assert.equal(getToken(), 'volatile-access-token');
    assert.ok(getTokenExpiry() > Date.now());
  } finally {
    setToken('');
    if (originalSession) Object.defineProperty(globalThis, 'sessionStorage', originalSession);
    else delete globalThis.sessionStorage;
    if (originalLocal) Object.defineProperty(globalThis, 'localStorage', originalLocal);
    else delete globalThis.localStorage;
  }
});

test('descarta tokens vencidos también en el fallback de memoria', () => {
  const originalSession = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalLocal = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  try {
    setToken('expired-token', { expiresAt: new Date(Date.now() - 1_000).toISOString() });
    assert.equal(getToken(), '');
    assert.equal(getTokenExpiry(), 0);
  } finally {
    setToken('');
    if (originalSession) Object.defineProperty(globalThis, 'sessionStorage', originalSession);
    else delete globalThis.sessionStorage;
    if (originalLocal) Object.defineProperty(globalThis, 'localStorage', originalLocal);
    else delete globalThis.localStorage;
  }
});

test('usa sessionStorage como respaldo si localStorage está bloqueado y se solicita recordar sesión', () => {
  const originalSession = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalLocal = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const values = new Map();
  const session = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: session });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('localStorage blocked'); } });
  try {
    setToken('remember-fallback-token', { persist: true, expiresAt: new Date(Date.now() + 60_000).toISOString() });
    assert.equal(getToken(), 'remember-fallback-token');
    assert.equal(values.get('inventary.accessToken'), 'remember-fallback-token');
  } finally {
    setToken('');
    if (originalSession) Object.defineProperty(globalThis, 'sessionStorage', originalSession);
    else delete globalThis.sessionStorage;
    if (originalLocal) Object.defineProperty(globalThis, 'localStorage', originalLocal);
    else delete globalThis.localStorage;
  }
});

test('adjunta el token volátil a las peticiones autenticadas', async () => {
  const originalSession = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalLocal = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const originalFetch = globalThis.fetch;
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('storage blocked'); } });
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.get('Authorization'), 'Bearer volatile-request-token');
    return new Response(JSON.stringify({ username: 'operator' }), {
      status: 200, headers: { 'Content-Type': 'application/json' }
    });
  };
  try {
    setToken('volatile-request-token', { expiresAt: new Date(Date.now() + 60_000).toISOString() });
    const profile = await request('/auth/me');
    assert.equal(profile.username, 'operator');
  } finally {
    setToken('');
    globalThis.fetch = originalFetch;
    if (originalSession) Object.defineProperty(globalThis, 'sessionStorage', originalSession);
    else delete globalThis.sessionStorage;
    if (originalLocal) Object.defineProperty(globalThis, 'localStorage', originalLocal);
    else delete globalThis.localStorage;
  }
});

test('no persiste en localStorage cuando el usuario no marcó mantener sesión', () => {
  const originalSession = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage');
  const originalLocal = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const values = new Map();
  const local = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('sessionStorage blocked'); } });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: local });
  try {
    setToken('tab-only-token', { persist: false, expiresAt: new Date(Date.now() + 60_000).toISOString() });
    assert.equal(getToken(), 'tab-only-token');
    assert.equal(values.has('inventary.accessToken'), false);
  } finally {
    setToken('');
    if (originalSession) Object.defineProperty(globalThis, 'sessionStorage', originalSession);
    else delete globalThis.sessionStorage;
    if (originalLocal) Object.defineProperty(globalThis, 'localStorage', originalLocal);
    else delete globalThis.localStorage;
  }
});

test('notifica a los suscriptores cuando una sesión termina para sincronizar pestañas', async () => {
  const { endSessionAcrossTabs, subscribeToSessionEnd } = await import('./request.js');
  let notifications = 0;
  const unsubscribe = subscribeToSessionEnd(() => { notifications += 1; });
  try {
    setToken('cross-tab-session-token', { expiresAt: new Date(Date.now() + 60_000).toISOString() });
    endSessionAcrossTabs();
    assert.equal(notifications, 1);
    assert.equal(getToken(), '');
  } finally {
    unsubscribe();
    setToken('');
  }
});

test('el logout remoto fallido no impide limpiar el token local', async () => {
  const { endSessionAcrossTabs } = await import('./request.js');
  setToken('logout-failure-token', { expiresAt: new Date(Date.now() + 60_000).toISOString() });
  endSessionAcrossTabs();
  assert.equal(getToken(), '');
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getToken, getTokenExpiry, setToken } from './request.js';

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

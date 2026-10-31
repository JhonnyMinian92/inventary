
export const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');
const TOKEN_KEY = 'inventary.accessToken';
const TOKEN_EXPIRY_KEY = 'inventary.accessTokenExpiresAt';
const IDEMPOTENCY_RETRY_WINDOW_MS = 120_000;
const pendingIdempotencyKeys = new Map();

function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  if (globalThis.crypto?.getRandomValues) {
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  }
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2) + '-' + Math.random().toString(36).slice(2);
}
function idempotencySignature(url, method, body) {
  return [method.toUpperCase(), url, typeof body === 'string' ? body : ''].join('|');
}
function getIdempotencyKey(signature) {
  const now = Date.now();
  for (const [key, value] of pendingIdempotencyKeys) {
    if (value.expiresAt <= now) pendingIdempotencyKeys.delete(key);
  }
  const existing = pendingIdempotencyKeys.get(signature);
  if (existing) return existing.key;
  const key = createIdempotencyKey();
  pendingIdempotencyKeys.set(signature, { key, expiresAt: now + IDEMPOTENCY_RETRY_WINDOW_MS });
  return key;
}

export class ApiError extends Error {
  constructor(message, status = 0, details = null, retryAfter = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
    this.code = details?.code || '';
    this.retryAfter = retryAfter;
  }
}

function storages() {
  const available = [];
  for (const name of ['sessionStorage', 'localStorage']) {
    try { if (globalThis[name]) available.push(globalThis[name]); } catch { /* almacenamiento bloqueado */ }
  }
  return available;
}
function readStoredToken() {
  for (const storage of storages()) {
    try {
      const token = storage.getItem(TOKEN_KEY);
      if (token) return { token, expiresAt: Number(storage.getItem(TOKEN_EXPIRY_KEY)) || 0 };
    } catch { /* almacenamiento bloqueado */ }
  }
  return null;
}
export function getToken() {
  const stored = readStoredToken();
  if (!stored) return '';
  if (stored.expiresAt && stored.expiresAt <= Date.now()) {
    setToken('');
    return '';
  }
  return stored.token;
}
export function getTokenExpiry() { return readStoredToken()?.expiresAt || 0; }
export function setToken(token, { persist = false, expiresAt = null } = {}) {
  const nextToken = token || '';
  if (readStoredToken()?.token !== nextToken) pendingIdempotencyKeys.clear();
  for (const storage of storages()) {
    try { storage.removeItem(TOKEN_KEY); storage.removeItem(TOKEN_EXPIRY_KEY); } catch { /* almacenamiento bloqueado */ }
  }
  if (!nextToken) return;
  const target = storages().find(storage => storage === (persist ? globalThis.localStorage : globalThis.sessionStorage)) || storages()[0];
  if (!target) return;
  const expiry = Date.parse(expiresAt || '');
  try {
    target.setItem(TOKEN_KEY, nextToken);
    if (Number.isFinite(expiry)) target.setItem(TOKEN_EXPIRY_KEY, String(expiry));
  } catch { /* almacenamiento bloqueado: la sesión dura lo que la pestaña */ }
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const token = getToken();
  if (token) headers.set('Authorization', 'Bearer ' + token);
  const url = API_BASE + (path.startsWith('/') ? path : '/' + path);
  const method = String(options.method || 'GET').toUpperCase();
  const requestBody = options.body && typeof options.body !== 'string' && !(options.body instanceof FormData)
    ? JSON.stringify(options.body) : options.body;
  const signature = options.idempotent ? idempotencySignature(url, method, requestBody) : null;
  let idempotencyKey = null;
  if (options.idempotencyKey) {
    idempotencyKey = options.idempotencyKey;
    headers.set('Idempotency-Key', idempotencyKey);
  } else if (options.idempotent) {
    idempotencyKey = getIdempotencyKey(signature);
    headers.set('Idempotency-Key', idempotencyKey);
  }
  const fetchOptions = { ...options };
  delete fetchOptions.idempotent;
  delete fetchOptions.idempotencyKey;
  let response;
  try {
    response = await fetch(url, { ...fetchOptions, method, headers, body: requestBody });
  } catch {
    throw new ApiError('No se pudo conectar con el backend. Verifica que la API esté activa y que CORS permita este origen.');
  }
  const type = response.headers.get('content-type') || '';
  const payload = response.status === 204 ? null : type.includes('json')
    ? await response.json().catch(() => null) : await response.text().catch(() => '');
  if (signature && idempotencyKey && (response.ok || (response.status < 500 && response.status !== 429))) {
    const cached = pendingIdempotencyKeys.get(signature);
    if (cached?.key === idempotencyKey) pendingIdempotencyKeys.delete(signature);
  }
  if (!response.ok) {
    if (response.status === 401) setToken('');
    const message = payload?.detail || payload?.message || payload?.title ||
      (typeof payload === 'string' ? payload : '') || 'Error HTTP ' + response.status;
    throw new ApiError(message, response.status, payload, Number(response.headers.get('Retry-After')) || 0);
  }
  return payload;
}

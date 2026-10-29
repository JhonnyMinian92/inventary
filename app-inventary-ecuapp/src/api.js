import { encryptCredentials } from './security/loginCrypto.js';

const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');
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
  // Idempotency keys identify a request; they are not authentication secrets.
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

// sessionStorage por defecto (la sesión termina al cerrar la pestaña); localStorage solo con "Mantener sesión iniciada".
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

export function getTokenExpiry() {
  return readStoredToken()?.expiresAt || 0;
}

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
    ? JSON.stringify(options.body)
    : options.body;
  const signature = options.idempotent
    ? idempotencySignature(url, method, requestBody)
    : null;
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
    response = await fetch(url, {
      ...fetchOptions,
      method,
      headers,
      body: requestBody
    });
  } catch {
    // Keep the key briefly so an identical retry after an ambiguous network failure
    // reuses the backend's idempotency record instead of posting a second transaction.
    throw new ApiError('No se pudo conectar con el backend. Verifica que la API esté activa y que CORS permita este origen.');
  }

  const type = response.headers.get('content-type') || '';
  const payload = response.status === 204 ? null : type.includes('json')
    ? await response.json().catch(() => null)
    : await response.text().catch(() => '');

  if (signature && idempotencyKey) {
    if (response.ok || (response.status < 500 && response.status !== 429)) {
      const cached = pendingIdempotencyKeys.get(signature);
      if (cached?.key === idempotencyKey) pendingIdempotencyKeys.delete(signature);
    }
  }

  if (!response.ok) {
    if (response.status === 401) setToken('');
    const message = payload?.detail || payload?.message || payload?.title ||
      (typeof payload === 'string' ? payload : '') || 'Error HTTP ' + response.status;
    throw new ApiError(message, response.status, payload, Number(response.headers.get('Retry-After')) || 0);
  }
  return payload;
}

export const api = {
  login: encryptedLogin,
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me'),
  products: (params = {}) => request(`/catalog/products${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  product: id => request(`/catalog/products/${encodeURIComponent(id)}`),
  productImages: id => request(`/catalog/products/${encodeURIComponent(id)}/images`),
  uploadProductImage: (id, file, options = {}) => {
    const body = new FormData();
    body.append('file', file);
    if (options.altText) body.append('altText', options.altText);
    body.append('sortOrder', String(options.sortOrder ?? 0));
    body.append('primaryImage', String(Boolean(options.primaryImage)));
    return request(`/catalog/products/${encodeURIComponent(id)}/images/upload`, { method: 'POST', body });
  },
  unpublishProductImage: (productId, imageId) => request(`/catalog/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`, { method: 'DELETE' }),
  createProduct: data => request('/catalog/products', { method: 'POST', body: data }),
  updateProduct: (id, data) => request(`/catalog/products/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteProduct: id => request(`/catalog/products/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  activateProduct: id => request(`/catalog/products/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateProduct: id => request(`/catalog/products/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  categories: (params = {}) => request(`/catalog/categories${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  createCategory: data => request('/catalog/categories', { method: 'POST', body: data }),
  updateCategory: (id, data) => request(`/catalog/categories/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteCategory: id => request(`/catalog/categories/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  activateCategory: id => request(`/catalog/categories/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateCategory: id => request(`/catalog/categories/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  units: (params = {}) => request(`/catalog/units${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  unitConversions: () => request('/catalog/unit-conversions'),
  createUnitConversion: data => request('/catalog/unit-conversions', { method: 'POST', body: data }),
  createUnit: data => request('/catalog/units', { method: 'POST', body: data }),
  unitDimensions: () => request('/catalog/unit-dimensions'),
  attributePresets: () => request('/catalog/attribute-presets'),
  categorySuggestion: text => request(`/catalog/attribute-presets/suggestion?${new URLSearchParams({ text })}`),
  productKinds: () => request('/catalog/product-kinds'),
  updateUnit: (id, data) => request(`/catalog/units/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteUnit: id => request(`/catalog/units/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  warehouses: (params = {}) => request(`/warehouses${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  warehouse: id => request(`/warehouses/${encodeURIComponent(id)}`),
  createWarehouse: data => request('/warehouses', { method: 'POST', body: data }),
  updateWarehouse: (id, data) => request(`/warehouses/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteWarehouse: id => request(`/warehouses/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  activateWarehouse: id => request(`/warehouses/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateWarehouse: id => request(`/warehouses/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  suppliers: (params = {}) => request(`/suppliers${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  supplier: id => request(`/suppliers/${encodeURIComponent(id)}`),
  createSupplier: data => request('/suppliers', { method: 'POST', body: data }),
  updateSupplier: (id, data) => request(`/suppliers/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteSupplier: id => request(`/suppliers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  activateSupplier: id => request(`/suppliers/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateSupplier: id => request(`/suppliers/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  customers: (params = {}) => request(`/customers${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  customer: id => request(`/customers/${encodeURIComponent(id)}`),
  createCustomer: data => request('/customers', { method: 'POST', body: data }),
  updateCustomer: (id, data) => request(`/customers/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deleteCustomer: id => request(`/customers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  activateCustomer: id => request(`/customers/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateCustomer: id => request(`/customers/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  balances: (params = {}) => request(`/inventory/balances${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  createInventoryAdjustment: data => request('/inventory/adjustments', { method: 'POST', body: data, idempotent: true }),
  balance: (productId, warehouseId) => request(`/inventory/balances/${encodeURIComponent(productId)}/${encodeURIComponent(warehouseId)}`),
  movements: (params = {}) => request(`/inventory/movements${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  inventoryBalanceReport: params => request(`/reports/inventory-balance?${new URLSearchParams(params)}`),
  inventoryValuationReport: (params = {}) => request(`/reports/valuation${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  salesReport: (params = {}) => request(`/reports/sales?${new URLSearchParams(params)}`),
  purchasesReport: (params = {}) => request(`/reports/purchases?${new URLSearchParams(params)}`),
  kardexReport: (params = {}) => request(`/reports/kardex?${new URLSearchParams(params)}`),
  lowStockReport: (params = {}) => request(`/reports/low-stock${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  purchaseOrders: (params = {}) => request(`/purchase-orders${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  purchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}`),
  cancelPurchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),
  createPurchaseOrder: data => request('/purchase-orders', { method: 'POST', body: data, idempotent: true }),
  submitPurchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}/submit`, { method: 'POST' }),
  approvePurchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}/approve`, { method: 'POST' }),
  createReceipt: (purchaseOrderId, data) => request(`/purchase-orders/${encodeURIComponent(purchaseOrderId)}/receipts`, { method: 'POST', body: data, idempotent: true }),
  goodsReceipts: (params = {}) => request(`/goods-receipts${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  goodsReceipt: id => request(`/goods-receipts/${encodeURIComponent(id)}`),
  purchaseReturns: (params = {}) => request(`/purchase-returns${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  purchaseReturn: id => request(`/purchase-returns/${encodeURIComponent(id)}`),
  createPurchaseReturn: data => request('/purchase-returns', { method: 'POST', body: data, idempotent: true }),
  categoryAttributes: id => request(`/catalog/categories/${encodeURIComponent(id)}/attributes`),
  saveCategoryAttributes: (id, data) => request(`/catalog/categories/${encodeURIComponent(id)}/attributes`, { method: 'PUT', body: data }),
  sales: (params = {}) => request(`/sales${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  salesReturns: (params = {}) => request(`/sales-returns${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  salesReturn: id => request(`/sales-returns/${encodeURIComponent(id)}`),
  createSaleReturn: data => request('/sales-returns', { method: 'POST', body: data, idempotent: true }),
  sale: id => request(`/sales/${encodeURIComponent(id)}`),
  requestSaleCancellation: (id, data) => request(`/sales/${encodeURIComponent(id)}/request-cancellation`, { method: 'POST', body: data, idempotent: true }),
  approveSaleCancellation: (id) => request(`/sales/${encodeURIComponent(id)}/approve-cancellation`, { method: 'POST', idempotent: true }),
  inventoryTransfers: (params = {}) => request(`/inventory/transfers${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  inventoryTransfer: id => request(`/inventory/transfers/${encodeURIComponent(id)}`),
  createInventoryTransfer: data => request('/inventory/transfers', { method: 'POST', body: data, idempotent: true }),
  dispatchInventoryTransfer: id => request(`/inventory/transfers/${encodeURIComponent(id)}/dispatch`, { method: 'POST', idempotent: true }),
  receiveInventoryTransfer: id => request(`/inventory/transfers/${encodeURIComponent(id)}/receive`, { method: 'POST', idempotent: true }),
  reverseInventoryTransfer: (id, reason) => request(`/inventory/transfers/${encodeURIComponent(id)}/reverse`, { method: 'POST', body: { reason }, idempotent: true }),
  cancelInventoryTransfer: (id, reason) => request(`/inventory/transfers/${encodeURIComponent(id)}/cancel`, { method: 'POST', body: { reason }, idempotent: true }),
  inventoryCounts: (params = {}) => request(`/inventory/counts${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  inventoryCount: id => request(`/inventory/counts/${encodeURIComponent(id)}`),
  createInventoryCount: data => request('/inventory/counts', { method: 'POST', body: data, idempotent: true }),
  postInventoryCount: id => request(`/inventory/counts/${encodeURIComponent(id)}/post`, { method: 'POST', idempotent: true }),
  cancelInventoryCount: id => request(`/inventory/counts/${encodeURIComponent(id)}/cancel`, { method: 'POST', idempotent: true }),
  reorderRules: (params = {}) => request(`/inventory/reorder-rules${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  createReorderRule: data => request('/inventory/reorder-rules', { method: 'POST', body: data }),
  updateReorderRule: (id, data) => request(`/inventory/reorder-rules/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  deactivateReorderRule: id => request(`/inventory/reorder-rules/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  replenishmentSuggestions: () => request('/inventory/replenishment-suggestions'),
  availableStock: (params = {}) => request(`/inventory/availability${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  createSale: data => request('/sales', { method: 'POST', body: data, idempotent: true }),
  whatsappOrders: (params = {}) => request(`/whatsapp-orders${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  whatsappOrder: id => request(`/whatsapp-orders/${encodeURIComponent(id)}`),
  confirmWhatsAppPayment: (id, data) => request(`/whatsapp-orders/${encodeURIComponent(id)}/confirm-payment`, { method: 'POST', body: data, idempotent: true })
};

// Las credenciales se cifran con un desafío de un solo uso. Si el desafío expiró entre ambas llamadas, se reintenta una vez.
async function encryptedLogin(username, password) {
  for (let attempt = 0; ; attempt++) {
    const challenge = await request('/auth/login-challenge', { method: 'POST' });
    const encrypted = await encryptCredentials(challenge, { username, password });
    try {
      return await request('/auth/login', { method: 'POST', body: { encrypted } });
    } catch (error) {
      if (attempt === 0 && error?.code === 'LOGIN_CHALLENGE_INVALID') continue;
      throw error;
    }
  }
}

export function resolveApiUrl(path) {
  if (!path) return '';
  // Las imágenes subidas se guardan como /api/v1/...: se resuelven contra la raíz real de la API (p. ej. /inventary/api).
  if (path.startsWith('/api/')) {
    const apiRoot = new URL(API_BASE.replace(/\/v\d+$/, '') + '/', window.location.href);
    return new URL(path.slice('/api/'.length), apiRoot).toString();
  }
  const base = new URL(API_BASE, window.location.href);
  return new URL(path, base).toString();
}

export function listFrom(payload) {
  if (Array.isArray(payload)) return payload;
  for (const key of ['items', 'content', 'data', 'results', 'records']) {
    if (Array.isArray(payload?.[key])) return payload[key];
  }
  return [];
}

export function identifier(item) {
  return item?.id ?? item?.productId ?? item?.categoryId ?? item?.sku ?? '';
}

// Loads every page for screens that intentionally display/search the whole collection.
// Page size is capped by the backend; sequential page reads avoid burst traffic and partial lists.
export async function listAllPages(fetchPage, params = {}, pageSize = 100) {
  const size = Math.max(1, Math.min(100, Number(params.size) || pageSize));
  const first = await fetchPage({ ...params, page: 0, size });
  if (Array.isArray(first) || !Number.isFinite(Number(first?.totalPages))) return listFrom(first);

  const totalPages = Math.max(1, Number(first.totalPages) || 1);
  if (totalPages > 1000) {
    throw new ApiError('El catálogo supera los 100.000 registros; usa filtros más específicos para evitar cargar un volumen excesivo en el navegador.', 413);
  }

  const all = [...listFrom(first)];
  for (let page = 1; page < totalPages; page++) {
    const response = await fetchPage({ ...params, page, size });
    all.push(...listFrom(response));
  }
  return all;
}


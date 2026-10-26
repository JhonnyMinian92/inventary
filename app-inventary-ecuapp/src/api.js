const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message, status = 0, details = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

export function getToken() {
  return localStorage.getItem('inventary.accessToken') || '';
}

export function setToken(token) {
  if (token) localStorage.setItem('inventary.accessToken', token);
  else localStorage.removeItem('inventary.accessToken');
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const token = getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.idempotent) headers.set('Idempotency-Key', crypto.randomUUID());
  let response;
  try {
    response = await fetch(`${API_BASE}${path.startsWith('/') ? path : `/${path}`}`, {
      ...options, headers, body: options.body && typeof options.body !== 'string' && !(options.body instanceof FormData) ? JSON.stringify(options.body) : options.body
    });
  } catch {
    throw new ApiError('No se pudo conectar con el backend. Verifica que la API esté activa y que CORS permita este origen.');
  }
  const type = response.headers.get('content-type') || '';
  const payload = response.status === 204 ? null : type.includes('json') ? await response.json().catch(() => null) : await response.text().catch(() => '');
  if (!response.ok) {
    if (response.status === 401) setToken('');
    const message = payload?.detail || payload?.message || payload?.title || (typeof payload === 'string' ? payload : '') || `Error HTTP ${response.status}`;
    throw new ApiError(message, response.status, payload);
  }
  return payload;
}

export const api = {
  login: (username, password) => request('/auth/login', { method: 'POST', body: { username, password } }),
  me: () => request('/auth/me'),
  products: (params = {}) => request(`/catalog/products${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  product: id => request(`/catalog/products/${encodeURIComponent(id)}`),
  createProduct: data => request('/catalog/products', { method: 'POST', body: data }),
  updateProduct: (id, data) => request(`/catalog/products/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  activateProduct: id => request(`/catalog/products/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateProduct: id => request(`/catalog/products/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  categories: () => request('/catalog/categories'),
  createCategory: data => request('/catalog/categories', { method: 'POST', body: data }),
  updateCategory: (id, data) => request(`/catalog/categories/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
  activateCategory: id => request(`/catalog/categories/${encodeURIComponent(id)}/activate`, { method: 'POST' }),
  deactivateCategory: id => request(`/catalog/categories/${encodeURIComponent(id)}/deactivate`, { method: 'POST' }),
  units: () => request('/catalog/units'),
  warehouses: () => request('/warehouses'),
  suppliers: () => request('/suppliers'),
  balances: (params = {}) => request(`/inventory/balances${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  movements: (params = {}) => request(`/inventory/movements${new URLSearchParams(params).size ? `?${new URLSearchParams(params)}` : ''}`),
  inventoryBalanceReport: params => request(`/reports/inventory-balance?${new URLSearchParams(params)}`),
  purchaseOrders: () => request('/purchase-orders'),
  createPurchaseOrder: data => request('/purchase-orders', { method: 'POST', body: data, idempotent: true }),
  submitPurchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}/submit`, { method: 'POST' }),
  approvePurchaseOrder: id => request(`/purchase-orders/${encodeURIComponent(id)}/approve`, { method: 'POST' }),
  createReceipt: (purchaseOrderId, data) => request(`/purchase-orders/${encodeURIComponent(purchaseOrderId)}/receipts`, { method: 'POST', body: data, idempotent: true }),
  categoryAttributes: id => request(`/catalog/categories/${encodeURIComponent(id)}/attributes`),
  saveCategoryAttributes: (id, data) => request(`/catalog/categories/${encodeURIComponent(id)}/attributes`, { method: 'PUT', body: data }),
  sales: () => request('/sales'),
  createSale: data => request('/sales', { method: 'POST', body: data, idempotent: true })
};

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

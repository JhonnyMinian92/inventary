
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const inventoryApi = {
  balances: (params = {}) => request('/inventory/balances' + query(params)),
  createInventoryAdjustment: data => request('/inventory/adjustments', { method: 'POST', body: data, idempotent: true }),
  balance: (productId, warehouseId) => request('/inventory/balances/' + encodeURIComponent(productId) + '/' + encodeURIComponent(warehouseId)),
  movements: (params = {}) => request('/inventory/movements' + query(params)),
  inventoryTransfers: (params = {}) => request('/inventory/transfers' + query(params)),
  inventoryTransfer: id => request('/inventory/transfers/' + encodeURIComponent(id)),
  createInventoryTransfer: data => request('/inventory/transfers', { method: 'POST', body: data, idempotent: true }),
  dispatchInventoryTransfer: id => request('/inventory/transfers/' + encodeURIComponent(id) + '/dispatch', { method: 'POST', idempotent: true }),
  receiveInventoryTransfer: id => request('/inventory/transfers/' + encodeURIComponent(id) + '/receive', { method: 'POST', idempotent: true }),
  reverseInventoryTransfer: (id, reason) => request('/inventory/transfers/' + encodeURIComponent(id) + '/reverse', { method: 'POST', body: { reason }, idempotent: true }),
  cancelInventoryTransfer: (id, reason) => request('/inventory/transfers/' + encodeURIComponent(id) + '/cancel', { method: 'POST', body: { reason }, idempotent: true }),
  inventoryCounts: (params = {}) => request('/inventory/counts' + query(params)),
  inventoryCount: id => request('/inventory/counts/' + encodeURIComponent(id)),
  createInventoryCount: data => request('/inventory/counts', { method: 'POST', body: data, idempotent: true }),
  postInventoryCount: id => request('/inventory/counts/' + encodeURIComponent(id) + '/post', { method: 'POST', idempotent: true }),
  cancelInventoryCount: id => request('/inventory/counts/' + encodeURIComponent(id) + '/cancel', { method: 'POST', idempotent: true }),
  reorderRules: (params = {}) => request('/inventory/reorder-rules' + query(params)),
  createReorderRule: data => request('/inventory/reorder-rules', { method: 'POST', body: data }),
  updateReorderRule: (id, data) => request('/inventory/reorder-rules/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deactivateReorderRule: id => request('/inventory/reorder-rules/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' }),
  replenishmentSuggestions: () => request('/inventory/replenishment-suggestions'),
  availableStock: (params = {}) => request('/inventory/availability' + query(params))
};

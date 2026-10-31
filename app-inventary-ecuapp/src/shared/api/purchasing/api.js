
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const purchasingApi = {
  purchaseOrders: (params = {}) => request('/purchase-orders' + query(params)),
  purchaseOrder: id => request('/purchase-orders/' + encodeURIComponent(id)),
  cancelPurchaseOrder: id => request('/purchase-orders/' + encodeURIComponent(id) + '/cancel', { method: 'POST' }),
  createPurchaseOrder: data => request('/purchase-orders', { method: 'POST', body: data, idempotent: true }),
  submitPurchaseOrder: id => request('/purchase-orders/' + encodeURIComponent(id) + '/submit', { method: 'POST' }),
  approvePurchaseOrder: id => request('/purchase-orders/' + encodeURIComponent(id) + '/approve', { method: 'POST' }),
  createReceipt: (purchaseOrderId, data) => request('/purchase-orders/' + encodeURIComponent(purchaseOrderId) + '/receipts', { method: 'POST', body: data, idempotent: true }),
  goodsReceipts: (params = {}) => request('/goods-receipts' + query(params)),
  goodsReceipt: id => request('/goods-receipts/' + encodeURIComponent(id)),
  purchaseReturns: (params = {}) => request('/purchase-returns' + query(params)),
  purchaseReturn: id => request('/purchase-returns/' + encodeURIComponent(id)),
  createPurchaseReturn: data => request('/purchase-returns', { method: 'POST', body: data, idempotent: true })
};

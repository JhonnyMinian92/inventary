
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const salesApi = {
  sales: (params = {}) => request('/sales' + query(params)),
  salesReturns: (params = {}) => request('/sales-returns' + query(params)),
  salesReturn: id => request('/sales-returns/' + encodeURIComponent(id)),
  createSaleReturn: data => request('/sales-returns', { method: 'POST', body: data, idempotent: true }),
  sale: id => request('/sales/' + encodeURIComponent(id)),
  requestSaleCancellation: (id, data) => request('/sales/' + encodeURIComponent(id) + '/request-cancellation', { method: 'POST', body: data, idempotent: true }),
  approveSaleCancellation: id => request('/sales/' + encodeURIComponent(id) + '/approve-cancellation', { method: 'POST', idempotent: true }),
  createSale: data => request('/sales', { method: 'POST', body: data, idempotent: true }),
  salePricing: data => request('/sales/pricing', { method: 'POST', body: data })
};

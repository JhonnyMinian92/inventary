
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const partnersApi = {
  warehouses: (params = {}) => request('/warehouses' + query(params)),
  warehouse: id => request('/warehouses/' + encodeURIComponent(id)),
  createWarehouse: data => request('/warehouses', { method: 'POST', body: data }),
  updateWarehouse: (id, data) => request('/warehouses/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteWarehouse: id => request('/warehouses/' + encodeURIComponent(id), { method: 'DELETE' }),
  activateWarehouse: id => request('/warehouses/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
  deactivateWarehouse: id => request('/warehouses/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' }),
  suppliers: (params = {}) => request('/suppliers' + query(params)),
  supplier: id => request('/suppliers/' + encodeURIComponent(id)),
  createSupplier: data => request('/suppliers', { method: 'POST', body: data }),
  updateSupplier: (id, data) => request('/suppliers/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteSupplier: id => request('/suppliers/' + encodeURIComponent(id), { method: 'DELETE' }),
  activateSupplier: id => request('/suppliers/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
  deactivateSupplier: id => request('/suppliers/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' }),
  customers: (params = {}) => request('/customers' + query(params)),
  customer: id => request('/customers/' + encodeURIComponent(id)),
  createCustomer: data => request('/customers', { method: 'POST', body: data }),
  updateCustomer: (id, data) => request('/customers/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteCustomer: id => request('/customers/' + encodeURIComponent(id), { method: 'DELETE' }),
  activateCustomer: id => request('/customers/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
  deactivateCustomer: id => request('/customers/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' })
};

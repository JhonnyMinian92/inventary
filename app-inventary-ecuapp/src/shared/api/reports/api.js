
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const reportsApi = {
  inventoryBalanceReport: params => request('/reports/inventory-balance?' + new URLSearchParams(params)),
  inventoryValuationReport: (params = {}) => request('/reports/valuation' + query(params)),
  salesReport: (params = {}) => request('/reports/sales?' + new URLSearchParams(params)),
  purchasesReport: (params = {}) => request('/reports/purchases?' + new URLSearchParams(params)),
  kardexReport: (params = {}) => request('/reports/kardex?' + new URLSearchParams(params)),
  lowStockReport: (params = {}) => request('/reports/low-stock' + query(params))
};

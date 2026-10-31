
import { authApi } from './auth/api.js';
import { catalogApi } from './catalog/api.js';
import { partnersApi } from './partners/api.js';
import { inventoryApi } from './inventory/api.js';
import { reportsApi } from './reports/api.js';
import { purchasingApi } from './purchasing/api.js';
import { salesApi } from './sales/api.js';
import { subscriptionsApi } from './subscriptions/api.js';
import { whatsappApi } from './whatsapp/api.js';

export const api = Object.freeze({
  ...authApi, ...catalogApi, ...partnersApi, ...inventoryApi, ...reportsApi,
  ...purchasingApi, ...salesApi, ...subscriptionsApi, ...whatsappApi
});
export { ApiError, request, getToken, getTokenExpiry, setToken } from './http/request.js';
export { listFrom, identifier, listAllPages, resolveApiUrl } from './utils.js';


import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const subscriptionsApi = {
  subscriptionOptions: () => request('/subscriptions/options'),
  subscriptionPreview: data => request('/subscriptions/preview', { method: 'POST', body: data }),
  subscriptions: (params = {}) => request('/subscriptions' + query(params)),
  subscription: id => request('/subscriptions/' + encodeURIComponent(id)),
  createSubscription: data => request('/subscriptions', { method: 'POST', body: data, idempotent: true }),
  subscriptionCancellationPreview: id => request('/subscriptions/' + encodeURIComponent(id) + '/cancellation-preview'),
  cancelSubscription: (id, reason) => request('/subscriptions/' + encodeURIComponent(id) + '/cancel', { method: 'POST', body: { reason } }),
  prepaySubscription: (id, discountPercent) => request('/subscriptions/' + encodeURIComponent(id) + '/prepay', { method: 'POST', body: { discountPercent } })
};


import { request } from '../http/request.js';
const query = params => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  return search.size ? '?' + search.toString() : '';
};
export const whatsappApi = {
  whatsappWorkSummary: () => request('/whatsapp-orders/work-summary'),
  whatsappOrders: (params = {}) => request('/whatsapp-orders' + query(params)),
  whatsappOrder: id => request('/whatsapp-orders/' + encodeURIComponent(id)),
  confirmWhatsAppPayment: (id, data) => request('/whatsapp-orders/' + encodeURIComponent(id) + '/confirm-payment', { method: 'POST', body: data, idempotent: true })
};

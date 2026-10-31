
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const whatsappApi = {
  whatsappOrders: (params = {}) => request('/whatsapp-orders' + query(params)),
  whatsappOrder: id => request('/whatsapp-orders/' + encodeURIComponent(id)),
  confirmWhatsAppPayment: (id, data) => request('/whatsapp-orders/' + encodeURIComponent(id) + '/confirm-payment', { method: 'POST', body: data, idempotent: true })
};

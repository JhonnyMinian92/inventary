// Formatos y etiquetas compartidos (es-EC).

const moneyFormat = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const compactMoneyFormat = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 });
const numberFormat = new Intl.NumberFormat('es-EC', { maximumFractionDigits: 3 });
const compactNumberFormat = new Intl.NumberFormat('es-EC', { notation: 'compact', maximumFractionDigits: 1 });

export function money(value) { return moneyFormat.format(Number(value || 0)); }
export function compactMoney(value) { return Math.abs(Number(value || 0)) >= 10000 ? compactMoneyFormat.format(Number(value || 0)) : money(value); }
export function number(value) { return numberFormat.format(Number(value || 0)); }
export function compactNumber(value) { return Math.abs(Number(value || 0)) >= 10000 ? compactNumberFormat.format(Number(value || 0)) : number(value); }

function parse(value) {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? null : parsed;
}
export function date(value) { return parse(value)?.toLocaleDateString('es-EC') || '—'; }
export function dateTime(value) { return parse(value)?.toLocaleString('es-EC', { dateStyle: 'short', timeStyle: 'short' }) || '—'; }
export function shortDate(value) { return parse(value)?.toLocaleDateString('es-EC', { day: '2-digit', month: 'short' }) || '—'; }

/** Fecha local YYYY-MM-DD (los reportes del backend usan fechas de calendario). */
export function isoDate(value = new Date()) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function relativeTime(value) {
  const parsed = parse(value);
  if (!parsed) return '—';
  const minutes = Math.round((parsed.getTime() - Date.now()) / 60000);
  const formatter = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
  if (Math.abs(minutes) < 60) return formatter.format(minutes, 'minute');
  if (Math.abs(minutes) < 1440) return formatter.format(Math.round(minutes / 60), 'hour');
  return formatter.format(Math.round(minutes / 1440), 'day');
}

export function titleCase(value = '') {
  return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
}

const STATUS_LABELS = {
  purchase: { DRAFT: 'Borrador', SUBMITTED: 'Por aprobar', APPROVED: 'Aprobada', PARTIALLY_RECEIVED: 'Recepción parcial', RECEIVED: 'Recibida', CANCELLED: 'Cancelada' },
  sale: { COMPLETED: 'Completada', CANCELLATION_REQUESTED: 'Anulación solicitada', CANCELLED: 'Anulada' },
  whatsapp: { AWAITING_PAYMENT: 'Por cobrar', CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', EXPIRED: 'Expirado' }
};
const STATUS_TONES = {
  DRAFT: 'neutral', SUBMITTED: 'pending', APPROVED: 'info', PARTIALLY_RECEIVED: 'pending', RECEIVED: 'active', CANCELLED: 'inactive',
  COMPLETED: 'active', CANCELLATION_REQUESTED: 'pending', AWAITING_PAYMENT: 'pending', CONFIRMED: 'active', EXPIRED: 'inactive'
};
export function statusLabel(kind, status) { return STATUS_LABELS[kind]?.[String(status || '').toUpperCase()] || titleCase(status); }
export function statusTone(status) { return STATUS_TONES[String(status || '').toUpperCase()] || 'neutral'; }

const MOVEMENTS = {
  ADJUSTMENT_IN: ['Ajuste de entrada', 1], ADJUSTMENT_OUT: ['Ajuste de salida', -1], SALE: ['Venta', -1], SALE_CANCEL_IN: ['Anulación de venta', 1],
  RECEIPT: ['Recepción de compra', 1], RETURN_IN: ['Devolución de cliente', 1], RETURN_OUT: ['Devolución a proveedor', -1],
  TRANSFER_IN: ['Traslado recibido', 1], TRANSFER_OUT: ['Traslado enviado', -1], RESERVE: ['Reserva', 0], RELEASE: ['Liberación de reserva', 0]
};
export function movementLabel(type) { return MOVEMENTS[String(type || '').toUpperCase()]?.[0] || titleCase(type); }
/** 1 entrada, -1 salida, 0 sin efecto sobre la existencia. */
export function movementDirection(type) { return MOVEMENTS[String(type || '').toUpperCase()]?.[1] ?? 0; }

export function isInactive(item) { return item?.active === false || String(item?.status || '').toUpperCase() === 'INACTIVE'; }
export function idOf(item) { return item?.id ?? item?.productId ?? item?.categoryId ?? item?.warehouseId ?? ''; }

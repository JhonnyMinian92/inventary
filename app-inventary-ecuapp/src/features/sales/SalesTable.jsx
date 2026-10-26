import { Ban, CheckCircle2 } from 'lucide-react';

function idOf(item) { return item?.id ?? item?.saleId ?? ''; }
function titleCase(value = '') { return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }
function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleDateString('es-EC');
}
function money(value, currency = 'USD') {
  if (value === null || value === undefined || value === '') return '—';
  try { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: currency || 'USD' }).format(Number(value)); }
  catch { return Number(value).toLocaleString('es-EC', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + currency; }
}

export default function SalesTable({ rows = [], loading = false, onRequestCancel, onApproveCancellation }) {
  return <div className="table-wrap">
    <table aria-label="Ventas registradas">
      <caption className="visually-hidden">Ventas y solicitudes de anulación</caption>
      <thead><tr><th scope="col">Documento</th><th scope="col">Estado</th><th scope="col">Cliente</th><th scope="col">Almacén</th><th scope="col">Fecha</th><th scope="col">Total</th><th scope="col">Acciones</th></tr></thead>
      <tbody>{rows.map((sale, index) => {
        const status = String(sale.status || 'COMPLETED').toUpperCase();
        const number = sale.saleNumber || sale.number || sale.code || String(idOf(sale)).slice(0, 12) || 'Venta';
        const canRequest = status === 'COMPLETED';
        const canApprove = status === 'CANCELLATION_REQUESTED';
        const tone = status === 'COMPLETED' ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending';
        return <tr key={idOf(sale) || index}>
          <td><strong>{number}</strong></td>
          <td><span className={'pill ' + tone}>{titleCase(status)}</span></td>
          <td>{sale.customerName || sale.customer?.name || 'Venta de mostrador'}</td>
          <td>{sale.warehouseCode || sale.warehouseName || sale.warehouse?.name || '—'}</td>
          <td>{date(sale.soldAt || sale.createdAt)}</td>
          <td>{money(sale.total, sale.currency)}</td>
          <td><div className="row-actions">
            {canRequest && <button type="button" className="small-action" disabled={loading} aria-label={'Solicitar anulación de venta ' + number} onClick={() => onRequestCancel(sale)}><Ban size={13}/> Solicitar anulación</button>}
            {canApprove && <button type="button" className="small-action" disabled={loading} aria-label={'Aprobar anulación de venta ' + number} onClick={() => onApproveCancellation(sale)}><CheckCircle2 size={13}/> Aprobar anulación</button>}
          </div></td>
        </tr>;
      })}</tbody>
    </table>
    {!rows.length && <div className="empty-state" role="status">Todavía no hay ventas registradas.</div>}
  </div>;
}

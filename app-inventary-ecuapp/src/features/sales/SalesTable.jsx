import { Ban, CheckCircle2, Eye } from 'lucide-react';
import { statusLabel, statusTone } from '../../utils/format.js';
import ServerPagination from '../../ui/ServerPagination.jsx';

function idOf(item) { return item?.id ?? item?.saleId ?? ''; }
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

export default function SalesTable({ rows = [], loading = false, onView, onRequestCancel, onApproveCancellation, pagination, page = 0, onPageChange }) {
  return <div className="table-wrap">
    <table aria-label="Ventas registradas">
      <caption className="visually-hidden">Ventas y solicitudes de anulación</caption>
      <thead><tr><th scope="col">Documento</th><th scope="col">Estado</th><th scope="col">Cliente</th><th scope="col">Almacén</th><th scope="col">Fecha</th><th scope="col">Total</th><th scope="col">Acciones</th></tr></thead>
      <tbody>{rows.map((sale, index) => {
        const status = String(sale.status || 'COMPLETED').toUpperCase();
        const number = sale.saleNumber || sale.number || sale.code || String(idOf(sale)).slice(0, 12) || 'Venta';
        const canRequest = status === 'COMPLETED';
        const canApprove = status === 'CANCELLATION_REQUESTED';
        return <tr key={idOf(sale) || index}>
          <td><strong>{number}</strong></td>
          <td><span className={'pill ' + statusTone(status)}>{statusLabel('sale', status)}</span></td>
          <td>{sale.customerName || sale.customer?.name || 'Venta de mostrador'}</td>
          <td>{sale.warehouseCode || sale.warehouseName || sale.warehouse?.name || '—'}</td>
          <td>{date(sale.soldAt || sale.createdAt)}</td>
          <td>{money(sale.total, sale.currency)}</td>
          <td><div className="row-actions">
            <button type="button" className="small-action" disabled={loading} aria-label={'Ver detalle de la venta ' + number} onClick={() => onView(sale)}><Eye size={13}/> Detalle</button>
            {canRequest && <button type="button" className="small-action" disabled={loading} aria-label={'Solicitar anulación de venta ' + number} onClick={() => onRequestCancel(sale)}><Ban size={13}/> Solicitar anulación</button>}
            {canApprove && <button type="button" className="small-action" disabled={loading} aria-label={'Aprobar anulación de venta ' + number} onClick={() => onApproveCancellation(sale)}><CheckCircle2 size={13}/> Aprobar anulación</button>}
          </div></td>
        </tr>;
      })}</tbody>
    </table>
    {!rows.length && <div className="empty-state" role="status">No hay ventas en esta página.</div>}
    <ServerPagination info={pagination} page={page} loading={loading} onChange={onPageChange} />
  </div>;
}

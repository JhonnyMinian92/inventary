import { CheckCircle2, Eye, Send, XCircle, PackageCheck } from 'lucide-react';

function idOf(item) { return item?.id ?? item?.purchaseOrderId ?? ''; }
function titleCase(value = '') { return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }
function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleDateString('es-EC');
}

export default function PurchaseTable({ rows = [], onView, onSubmit, onApprove, onCancel, onReceive, loading = false }) {
  return <div className="table-wrap">
    <table aria-label="Órdenes de compra">
      <caption className="visually-hidden">Órdenes de compra y sus estados de aprobación y recepción</caption>
      <thead><tr><th scope="col">Documento</th><th scope="col">Estado</th><th scope="col">Proveedor</th><th scope="col">Almacén</th><th scope="col">Líneas</th><th scope="col">Fecha</th><th scope="col">Acciones</th></tr></thead>
      <tbody>{rows.map((order, index) => {
        const status = String(order.status || 'DRAFT').toUpperCase();
        const items = Array.isArray(order.items) ? order.items : Array.isArray(order.lines) ? order.lines : [];
        const orderNumber = order.orderNumber || order.number || order.code || String(idOf(order)).slice(0, 12) || 'Orden';
        const canSubmit = status === 'DRAFT';
        const canApprove = status === 'SUBMITTED';
        const canReceive = ['APPROVED', 'PARTIALLY_RECEIVED'].includes(status);
        const canCancel = ['DRAFT', 'SUBMITTED'].includes(status);
        const tone = status === 'RECEIVED' ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending';
        return <tr key={idOf(order) || index}>
          <td><strong>{orderNumber}</strong></td>
          <td><span className={'pill ' + tone}>{titleCase(status)}</span></td>
          <td>{order.supplierName || order.supplier?.name || '—'}</td>
          <td>{order.warehouseCode || order.warehouseName || order.warehouse?.name || '—'}</td>
          <td>{items.length || order.itemCount || '—'}</td>
          <td>{date(order.orderedAt || order.createdAt)}</td>
          <td><div className="row-actions">
            <button type="button" className="small-action" disabled={loading} aria-label={'Ver detalle de la orden ' + orderNumber} onClick={() => onView(order)}><Eye size={13}/> Detalle</button>
            {canSubmit && <button type="button" className="small-action" disabled={loading} aria-label={'Enviar orden ' + orderNumber} onClick={() => onSubmit(order)}><Send size={13}/> Enviar</button>}
            {canApprove && <button type="button" className="small-action" disabled={loading} aria-label={'Aprobar orden ' + orderNumber} onClick={() => onApprove(order)}><CheckCircle2 size={13}/> Aprobar</button>}
            {canReceive && <button type="button" className="small-action" disabled={loading} aria-label={'Recibir mercancía de la orden ' + orderNumber} onClick={() => onReceive(order)}><PackageCheck size={13}/> Recibir</button>}
            {canCancel && <button type="button" className="small-action danger-text" disabled={loading} aria-label={'Cancelar orden ' + orderNumber} onClick={() => onCancel(order)}><XCircle size={13}/> Cancelar</button>}
          </div></td>
        </tr>;
      })}</tbody>
    </table>
    {!rows.length && <div className="empty-state" role="status">Todavía no hay órdenes de compra.</div>}
  </div>;
}

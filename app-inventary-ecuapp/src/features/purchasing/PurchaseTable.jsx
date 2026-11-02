import { CheckCircle2, Eye, Send, XCircle, PackageCheck, Pencil, Search, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { statusLabel, statusTone } from '../../utils/format.js';
import ServerPagination from '../../ui/ServerPagination.jsx';

function idOf(item) { return item?.id ?? item?.purchaseOrderId ?? ''; }
function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleDateString('es-EC');
}

export default function PurchaseTable({ rows = [], onView, onEdit, onDelete, onSubmit, onApprove, onCancel, onReceive, loading = false, pagination, page = 0, query = '', statusFilter = '', onStatusChange, onSearch, onPageChange, can = () => false }) {
  const [draftQuery, setDraftQuery] = useState(query);
  useEffect(() => setDraftQuery(query), [query]);
  return <div className="table-wrap">
    <form className="filter-bar" role="search" onSubmit={event => { event.preventDefault(); onSearch?.(draftQuery); }}>
      <label className="visually-hidden" htmlFor="purchase-order-search">Buscar órdenes por número o proveedor</label>
      <input id="purchase-order-search" type="search" maxLength={120} value={draftQuery}
        onChange={event => setDraftQuery(event.target.value)} placeholder="Buscar por número o proveedor…" />
      <button type="submit" className="button secondary" disabled={loading}><Search size={15}/> Buscar</button>
      {query && <button type="button" className="button secondary" disabled={loading}
        onClick={() => { setDraftQuery(''); onSearch?.(''); }}>Limpiar</button>}
      <label className="visually-hidden" htmlFor="purchase-status-filter">Estado</label>
      <select className="purchase-status-control" id="purchase-status-filter" value={statusFilter} onChange={event => onStatusChange?.(event.target.value)} aria-label="Filtrar por estado">
        <option value="">Todos los estados</option>
        {['DRAFT','SUBMITTED','APPROVED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED'].map(value => <option key={value} value={value}>{statusLabel('purchase', value)}</option>)}
      </select>
    </form>
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
        const canCancel = ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(status);
        return <tr key={idOf(order) || index}>
          <td><strong>{orderNumber}</strong></td>
          <td><span className={'pill ' + statusTone(status)}>{statusLabel('purchase', status)}</span></td>
          <td>{order.supplierName || order.supplier?.name || '—'}</td>
          <td>{order.warehouseCode || order.warehouseName || order.warehouse?.name || '—'}</td>
          <td>{items.length || order.itemCount || '—'}</td>
          <td>{date(order.orderedAt || order.createdAt)}</td>
          <td><div className="row-actions">
            <button type="button" className="small-action" disabled={loading} aria-label={'Ver detalle de la orden ' + orderNumber} onClick={() => onView(order)}><Eye size={13}/> Detalle</button>
            {canSubmit && can('purchasing:manage') && <button type="button" className="small-action" disabled={loading} onClick={() => onEdit(order)}><Pencil size={13}/> Editar borrador</button>}
            {(canSubmit || status === 'CANCELLED') && can('purchasing:manage') && <button type="button" className="small-action danger-text" disabled={loading} onClick={() => onDelete(order)}><Trash2 size={13}/> Eliminar borrador</button>}
            {canSubmit && <button type="button" className="small-action" disabled={loading} aria-label={'Enviar orden ' + orderNumber} onClick={() => onSubmit(order)}><Send size={13}/> Enviar</button>}
            {canApprove && <button type="button" className="small-action" disabled={loading} aria-label={'Aprobar orden ' + orderNumber} onClick={() => onApprove(order)}><CheckCircle2 size={13}/> Aprobar</button>}
            {canReceive && <button type="button" className="small-action" disabled={loading} aria-label={'Recibir mercancía de la orden ' + orderNumber} onClick={() => onReceive(order)}><PackageCheck size={13}/> Recibir</button>}
            {canCancel && <button type="button" className="small-action danger-text" disabled={loading} aria-label={'Cancelar orden ' + orderNumber} onClick={() => onCancel(order)}><XCircle size={13}/> Cancelar</button>}
          </div></td>
        </tr>;
      })}</tbody>
    </table>
    {!rows.length && <div className="empty-state" role="status">{statusFilter ? 'No hay órdenes de compra con el estado seleccionado.' : query ? 'No se encontraron órdenes con esa búsqueda.' : 'Todavía no hay órdenes de compra.'}</div>}
    <ServerPagination info={pagination} page={page} loading={loading} onChange={onPageChange} />
  </div>;
}

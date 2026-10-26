import DataTable from './DataTable.jsx';

function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function titleCase(value) { return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }
function dateTime(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleString('es-EC');
}
function money(value, currency = 'USD') {
  if (value === null || value === undefined || value === '') return '—';
  try { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: currency || 'USD' }).format(Number(value)); }
  catch { return Number(value).toLocaleString('es-EC', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + currency; }
}
function Definition({ label, value }) {
  return <div className="detail-definition"><dt>{label}</dt><dd>{value === null || value === undefined || value === '' ? '—' : value}</dd></div>;
}
function DetailGrid({ children }) { return <dl className="detail-grid">{children}</dl>; }

export function PurchaseOrderDetail({ order }) {
  const currency = order?.currency || 'USD';
  const items = Array.isArray(order?.items) ? order.items : [];
  const status = String(order?.status || '—').toUpperCase();
  return <div className="transaction-detail">
    <div className="detail-status-line"><span className={'pill ' + (status === 'RECEIVED' ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending')}>{titleCase(status)}</span><span className="muted">Creada: {dateTime(order?.orderedAt)}</span></div>
    <DetailGrid>
      <Definition label="Proveedor" value={order?.supplierName}/>
      <Definition label="Almacén de destino" value={order?.warehouseCode}/>
      <Definition label="Creada por" value={order?.createdBy}/>
      <Definition label="Enviada por" value={order?.submittedBy}/>
      <Definition label="Fecha de envío" value={dateTime(order?.submittedAt)}/>
      <Definition label="Aprobada por" value={order?.approvedBy}/>
      <Definition label="Fecha de aprobación" value={dateTime(order?.approvedAt)}/>
      <Definition label="Cancelada por" value={order?.cancelledBy}/>
      <Definition label="Fecha de cancelación" value={dateTime(order?.cancelledAt)}/>
      <Definition label="Moneda" value={currency}/>
      <Definition label="Subtotal" value={money(order?.subtotal, currency)}/>
      <Definition label="Total" value={money(order?.total, currency)}/>
      <Definition label="Notas" value={order?.notes}/>
    </DetailGrid>
    <div className="report-subheading"><div><h3>Artículos de la orden</h3><p>La cantidad recibida y la pendiente se muestran según el estado más reciente devuelto por la API.</p></div><span className="muted">{items.length} líneas</span></div>
    <DataTable rows={items} caption="Líneas del detalle de la orden de compra" empty="Esta orden no contiene líneas." columns={[
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' },
      { key: 'orderedQuantity', label: 'Solicitado' }, { key: 'receivedQuantity', label: 'Recibido' },
      { key: 'remainingQuantity', label: 'Pendiente' },
      { key: 'unitCost', label: 'Costo unitario', render: item => money(item.unitCost, currency) },
      { key: 'lineTotal', label: 'Total de línea', render: item => money(item.lineTotal, currency) }
    ]}/>
  </div>;
}

export function SaleDetail({ sale }) {
  const currency = sale?.currency || 'USD';
  const items = Array.isArray(sale?.items) ? sale.items : [];
  const status = String(sale?.status || '—').toUpperCase();
  return <div className="transaction-detail">
    <div className="detail-status-line"><span className={'pill ' + (status === 'COMPLETED' ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending')}>{titleCase(status)}</span><span className="muted">Registrada: {dateTime(sale?.soldAt)}</span></div>
    <DetailGrid>
      <Definition label="Cliente" value={sale?.customerName || 'Venta de mostrador'}/>
      <Definition label="Teléfono" value={sale?.customerPhone}/>
      <Definition label="Almacén" value={sale?.warehouseCode}/>
      <Definition label="Canal" value={titleCase(sale?.channel)}/>
      <Definition label="Registrada por" value={sale?.createdBy}/>
      <Definition label="Moneda" value={currency}/>
      <Definition label="Subtotal" value={money(sale?.subtotal, currency)}/>
      <Definition label="Impuesto" value={money(sale?.tax, currency)}/>
      <Definition label="Total" value={money(sale?.total, currency)}/>
      <Definition label="Solicitó anulación" value={sale?.cancellationRequestedBy}/>
      <Definition label="Fecha de solicitud" value={dateTime(sale?.cancellationRequestedAt)}/>
      <Definition label="Aprobó anulación" value={sale?.cancellationApprovedBy}/>
      <Definition label="Fecha de aprobación" value={dateTime(sale?.cancellationApprovedAt)}/>
      <Definition label="Motivo de anulación" value={sale?.cancellationReason}/>
    </DetailGrid>
    <div className="report-subheading"><div><h3>Artículos vendidos</h3><p>Precios, cantidades y totales según el documento original.</p></div><span className="muted">{items.length} líneas</span></div>
    <DataTable rows={items} caption="Líneas del detalle de venta" empty="Esta venta no contiene líneas." columns={[
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' },
      { key: 'quantity', label: 'Cantidad' },
      { key: 'unitPrice', label: 'Precio unitario', render: item => money(item.unitPrice, currency) },
      { key: 'lineTotal', label: 'Total de línea', render: item => money(item.lineTotal, currency) }
    ]}/>
  </div>;
}

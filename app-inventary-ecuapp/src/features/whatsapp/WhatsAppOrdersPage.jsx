import { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, CircleDollarSign, MessageCircle, Search } from 'lucide-react';
import { api, listFrom } from '../../api';
import Drawer from '../../ui/Drawer.jsx';
import { SkeletonTable } from '../../ui/Skeleton.jsx';
import { toast } from '../../ui/feedback.js';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';
import { dateTime, money, number, relativeTime, statusLabel, statusTone } from '../../utils/format.js';
import '../catalog/catalog.css';

const PAGE_SIZE = 20;
const STATUSES = [['AWAITING_PAYMENT', 'Por cobrar'], ['CONFIRMED', 'Confirmados'], ['EXPIRED', 'Expirados'], ['CANCELLED', 'Cancelados'], ['', 'Todos']];
const METHODS = [['TRANSFER', 'Transferencia'], ['CASH', 'Efectivo'], ['CARD', 'Tarjeta'], ['DEPOSIT', 'Depósito']];
const REFERENCE_PATTERN = /^[A-Za-z0-9._:/#-]{3,120}$/;

function ConfirmPaymentModal({ order, onClose, onConfirmed }) {
  const [method, setMethod] = useState('TRANSFER');
  const [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const dialogRef = useAccessibleDialog(true, () => { if (!busy) onClose(); });
  async function submit(event) {
    event.preventDefault();
    const value = reference.trim();
    if (!REFERENCE_PATTERN.test(value)) {
      setInvalid(true);
      toast.error('La referencia debe tener entre 3 y 120 caracteres: letras, números y . _ : / # -');
      return;
    }
    setBusy(true);
    try {
      const confirmed = await api.confirmWhatsAppPayment(order.id, { paymentMethod: method, paymentReference: value });
      toast.success(`Pago confirmado. El pedido ${confirmed.orderNumber} ya es una venta.`);
      onConfirmed(confirmed);
    } catch (error) {
      toast.error(error?.message || 'No se pudo confirmar el pago.');
      setBusy(false);
    }
  }
  return <div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <form ref={dialogRef} className="modal-card payment-card" role="dialog" aria-modal="true" aria-labelledby="payment-title" onSubmit={submit} tabIndex={-1}>
      <div className="modal-heading"><h2 id="payment-title">Confirmar pago</h2></div>
      <p className="payment-total">{order.orderNumber} · <strong>{money(order.total)}</strong></p>
      <fieldset className="method-options">
        <legend>Método de pago</legend>
        {METHODS.map(([value, label]) => <label key={value} className={method === value ? 'selected' : ''}>
          <input type="radio" name="method" value={value} checked={method === value} onChange={() => setMethod(value)} />{label}
        </label>)}
      </fieldset>
      <label className="field"><span>Referencia del pago <b>*</b></span>
        <input value={reference} maxLength={120} required aria-invalid={invalid} autoComplete="off" placeholder="Nº de comprobante o transacción"
          onChange={event => { setReference(event.target.value); setInvalid(false); }} />
      </label>
      <div className="form-actions">
        <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancelar</button>
        <button type="submit" className="button primary" disabled={busy}><CircleDollarSign size={16} />{busy ? 'Confirmando…' : 'Confirmar y registrar venta'}</button>
      </div>
    </form>
  </div>;
}

function OrderDrawer({ orderId, can, onClose, onChanged }) {
  const [order, setOrder] = useState(null);
  const [paying, setPaying] = useState(false);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    let active = true;
    setOrder(null);
    api.whatsappOrder(orderId)
      .then(detail => { if (active) setOrder(detail); })
      .catch(error => { if (active) { toast.error(error?.message || 'No se pudo cargar el pedido.'); closeRef.current(); } });
    return () => { active = false; };
  }, [orderId]);
  const awaiting = order?.status === 'AWAITING_PAYMENT';
  return <>
    <Drawer open title={order ? order.orderNumber : 'Pedido'} subtitle={order ? order.customerRef : 'Cargando…'} onClose={onClose}
      footer={order && awaiting && can('sales:manage') && <button type="button" className="button primary" onClick={() => setPaying(true)}><CircleDollarSign size={16} />Confirmar pago</button>}>
      {!order ? <SkeletonTable rows={4} columns={3} label="Cargando pedido" /> : <>
        <div className="detail-pills"><span className={'pill ' + statusTone(order.status)}>{statusLabel('whatsapp', order.status)}</span><span className="pill neutral">{order.warehouseCode}</span></div>
        <dl className="metric-grid">
          <div><dt>Total</dt><dd>{money(order.total)}</dd></div>
          <div><dt>Subtotal</dt><dd>{money(order.subtotal)}</dd></div>
          <div><dt>Impuestos</dt><dd>{money(order.tax)}</dd></div>
          <div><dt>Creado</dt><dd>{dateTime(order.createdAt)}</dd></div>
          {awaiting && <div><dt>Vence</dt><dd>{relativeTime(order.expiresAt)}</dd></div>}
          {order.confirmedAt && <div><dt>Pagado</dt><dd>{dateTime(order.confirmedAt)}</dd></div>}
        </dl>
        {order.paymentReference && <p className="drawer-text">Pago {order.paymentMethod} · ref. {order.paymentReference} · confirmado por {order.confirmedBy}</p>}
        <h3 className="drawer-subtitle">Artículos</h3>
        <ul className="line-items">{listFrom(order.items).map(item => <li key={item.productId}>
          <span><strong>{item.productName}</strong><small>{item.sku} · {number(item.quantity)} × {money(item.unitPrice)}</small></span>
          <strong>{money(item.lineTotal)}</strong>
        </li>)}</ul>
      </>}
    </Drawer>
    {paying && order && <ConfirmPaymentModal order={order} onClose={() => setPaying(false)} onConfirmed={confirmed => { setPaying(false); setOrder(confirmed); onChanged(); }} />}
  </>;
}

export default function WhatsAppOrdersPage({ can, detailId, onOpenDetail, onCloseDetail, onChanged }) {
  const [status, setStatus] = useState('AWAITING_PAYMENT');
  const [customer, setCustomer] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ loading: true, rows: [], total: 0, pages: 1 });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setCustomer(query.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [query]);
  useEffect(() => { setPage(0); }, [status, customer]);

  useEffect(() => {
    let active = true;
    setResult(current => ({ ...current, loading: true }));
    const params = { page, size: PAGE_SIZE, sort: 'createdAt,desc' };
    if (status) params.status = status;
    if (customer) params.customerRef = customer;
    api.whatsappOrders(params)
      .then(payload => {
        if (!active) return;
        const meta = payload?.page || payload || {};
        setResult({ loading: false, rows: listFrom(payload), total: Number(meta.totalElements || 0), pages: Math.max(1, Number(meta.totalPages || 1)) });
      })
      .catch(error => {
        if (!active) return;
        setResult({ loading: false, rows: [], total: 0, pages: 1 });
        toast.error(error?.message || 'No se pudieron cargar los pedidos.');
      });
    return () => { active = false; };
  }, [status, customer, page, reloadKey]);

  const refresh = useCallback(() => { setReloadKey(key => key + 1); onChanged?.(); }, [onChanged]);

  return <div className="page">
    <div className="filter-bar">
      <div className="segmented" role="group" aria-label="Estado del pedido">
        {STATUSES.map(([value, label]) => <button key={value || 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>)}
      </div>
      <label className="search-field"><Search size={16} aria-hidden="true" /><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Cliente (número de WhatsApp)" aria-label="Filtrar por cliente" /></label>
    </div>
    {result.loading && !result.rows.length ? <SkeletonTable rows={6} columns={6} label="Cargando pedidos" /> : <div className={'table-wrap data-table' + (result.loading ? ' is-refreshing' : '')}>
      <table aria-label="Pedidos de WhatsApp">
        <thead><tr><th scope="col">Pedido</th><th scope="col">Cliente</th><th scope="col">Almacén</th><th scope="col" className="num">Artículos</th><th scope="col" className="num">Total</th><th scope="col">Vence / creado</th><th scope="col">Estado</th></tr></thead>
        <tbody>{result.rows.map(order => <tr key={order.id} className="clickable" onClick={() => onOpenDetail(order.id)}>
          <td><button type="button" className="row-title" onClick={event => { event.stopPropagation(); onOpenDetail(order.id); }}><strong>{order.orderNumber}</strong><small>{dateTime(order.createdAt)}</small></button></td>
          <td>{order.customerRef}</td>
          <td>{order.warehouseCode}</td>
          <td className="num">{number(listFrom(order.items).length)}</td>
          <td className="num">{money(order.total)}</td>
          <td>{order.status === 'AWAITING_PAYMENT' ? relativeTime(order.expiresAt) : relativeTime(order.createdAt)}</td>
          <td><span className={'pill ' + statusTone(order.status)}>{statusLabel('whatsapp', order.status)}</span></td>
        </tr>)}</tbody>
      </table>
      {!result.rows.length && <div className="empty-block"><MessageCircle size={28} /><strong>Sin pedidos</strong><span>Los pedidos que lleguen por WhatsApp aparecerán aquí.</span></div>}
      {result.pages > 1 && <div className="pagination">
        <span>Página {page + 1} de {result.pages} · {number(result.total)} pedidos</span>
        <button type="button" className="icon-button" aria-label="Página anterior" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /></button>
        <button type="button" className="icon-button" aria-label="Página siguiente" disabled={page >= result.pages - 1} onClick={() => setPage(page + 1)}><ChevronRight size={16} /></button>
      </div>}
    </div>}
    {detailId && <OrderDrawer orderId={detailId} can={can} onClose={onCloseDetail} onChanged={refresh} />}
  </div>;
}

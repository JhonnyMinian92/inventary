import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronLeft, ChevronRight, CircleDollarSign, Copy, MessageCircle, RefreshCw, Search } from 'lucide-react';
import { api, listFrom } from '../../api';
import { SkeletonTable } from '../../ui/Skeleton.jsx';
import { toast } from '../../ui/feedback.js';
import { dateTime, money, number, relativeTime, statusLabel, statusTone } from '../../utils/format.js';
import '../catalog/catalog.css';

const PAGE_SIZE = 20;
const QUEUES = [
  ['AWAITING_PAYMENT', 'Por cobrar'],
  ['CONFIRMED', 'Confirmados'],
  ['EXPIRED', 'Expirados'],
  ['CANCELLED', 'Cancelados'],
  ['', 'Todos']
];
const METHODS = [['TRANSFER', 'Transferencia'], ['CASH', 'Efectivo'], ['CARD', 'Tarjeta'], ['DEPOSIT', 'Depósito']];
const REFERENCE_PATTERN = /^[A-Za-z0-9._:/#-]{3,120}$/;

function maskedCustomer(value) {
  const text = String(value || '');
  if (text.length <= 7) return text;
  return text.slice(0, 3) + '•••' + text.slice(-3);
}

function ConfirmPayment({ order, onClose, onConfirmed }) {
  const [method, setMethod] = useState('TRANSFER');
  const [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventEvent?.();
    event.preventDefault();
    if (!REFERENCE_PATTERN.test(reference.trim())) {
      toast.error('La referencia debe tener entre 3 y 120 caracteres válidos.');
      return;
    }
    setBusy(true);
    try {
      const confirmed = await api.confirmWhatsAppPayment(order.id, { paymentMethod: method, paymentReference: reference.trim() });
      toast.success(`Pago confirmado: pedido ${confirmed.orderNumber} convertido en venta.`);
      onConfirmed(confirmed);
    } catch (error) {
      toast.error(error?.message || 'No se pudo confirmar el pago.');
      setBusy(false);
    }
  }

  return <div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <form className="modal-card payment-card" role="dialog" aria-modal="true" aria-labelledby="payment-title" onSubmit={submit}>
      <div className="modal-heading"><h2 id="payment-title">Confirmar pago recibido</h2></div>
      <p className="payment-total">{order.orderNumber} · <strong>{money(order.total)}</strong></p>
      <fieldset className="method-options"><legend>Método de pago</legend>
        {METHODS.map(([value, label]) => <label key={value} className={method === value ? 'selected' : ''}>
          <input type="radio" name="method" value={value} checked={method === value} onChange={() => setMethod(value)} />{label}
        </label>)}
      </fieldset>
      <label className="field"><span>Referencia del pago <b>*</b></span>
        <input value={reference} maxLength={120} required placeholder="Comprobante o Nº de transacción" onChange={event => setReference(event.target.value)} />
      </label>
      <div className="form-actions">
        <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancelar</button>
        <button type="submit" className="button primary" disabled={busy}><CircleDollarSign size={16} />{busy ? 'Confirmando…' : 'Confirmar pago'}</button>
      </div>
    </form>
  </div>;
}

function OrderAccordion({ order, expanded, onToggle, can, onChanged, onCopy }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    if (!expanded || detail) return;
    let active = true;
    setLoading(true);
    setLoadError('');
    api.whatsappOrder(order.id)
      .then(value => { if (active) setDetail(value); })
      .catch(error => { if (active) setLoadError(error?.message || 'No se pudo cargar el detalle del pedido.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [expanded, order.id, detail]);

  const current = detail || order;
  const itemCount = listFrom(current.items).length;
  const quantity = listFrom(current.items).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
  const awaiting = current.status === 'AWAITING_PAYMENT';
  const ariaId = `wa-order-${order.id}`;

  async function copyOrder() {
    await navigator.clipboard?.writeText(current.orderNumber);
    toast.success('Número de pedido copiado.');
    onCopy?.();
  }

  return <>
    <article className={'wa-order-row ' + (expanded ? 'expanded' : '')}>
      <div className="wa-order-summary">
        <div className="wa-order-identity">
          <button type="button" className="wa-disclosure" aria-expanded={expanded} aria-controls={ariaId} onClick={onToggle}>
            <ChevronDown size={18} />
            <span className="sr-only">{expanded ? 'Ocultar' : 'Ver'} detalles del pedido {current.orderNumber}</span>
          </button>
          <div><strong>{current.orderNumber}</strong><small>{dateTime(current.createdAt)} · {relativeTime(current.createdAt)}</small></div>
        </div>
        <div><strong>{maskedCustomer(current.customerRef)}</strong><small>Cliente WhatsApp</small></div>
        <div><strong>{number(quantity)} uds · {number(itemCount)} líneas</strong><small>{current.warehouseCode || 'Almacén por confirmar'}</small></div>
        <div><strong>{money(current.total)}</strong><small>{current.currency || 'Moneda cotizada'}</small></div>
        <div className="wa-state-group">
          <span className={'pill ' + statusTone(current.status)}>{statusLabel('whatsapp', current.status)}</span>
          <span className="pill neutral">{awaiting ? 'Pago pendiente' : current.saleId ? 'Venta vinculada' : 'Pago registrado'}</span>
        </div>
        <button type="button" className="button secondary wa-primary-action" onClick={expanded ? copyOrder : onToggle}>
          {expanded ? <><Copy size={15} /> Copiar</> : awaiting ? 'Revisar pago' : current.saleId ? 'Ver seguimiento' : 'Ver detalle'}
        </button>
      </div>
      {expanded && <div className="wa-order-expanded" id={ariaId}>
        <div className="wa-decision-bar">
          <div><span>Próxima acción</span><strong>{awaiting ? 'Confirmar el pago recibido' : current.saleId ? 'Venta vinculada y pendiente de seguimiento' : 'Pedido registrado'}</strong></div>
          <div className="wa-decision-actions">
            <button type="button" className="button secondary" onClick={copyOrder}><Copy size={15} /> Copiar pedido</button>
            {awaiting && can('sales:manage') && <button type="button" className="button primary" onClick={() => setPaying(true)}><CircleDollarSign size={16} />Confirmar pago</button>}
          </div>
        </div>
        {loading && <div className="wa-detail-loading"><SkeletonTable rows={3} columns={3} label="Cargando pedido" /></div>}
        {loadError && <div className="alert error" role="alert">{loadError}</div>}
        {!loading && !loadError && <>
          {awaiting && (!current.expiresAt || new Date(current.expiresAt) <= new Date()) && <div className="wa-block-alert">La cotización venció. Confirme la vigencia antes de cobrar o aceptar.</div>}
          <div className="wa-detail-grid">
            <section>
              <h3>Cliente e identidad</h3>
              <dl className="wa-data-list">
                <div><dt>Contacto</dt><dd>{maskedCustomer(current.customerRef)}</dd></div>
                <div><dt>Canal</dt><dd>WhatsApp</dd></div>
                <div><dt>Origen</dt><dd>Conversación comercial</dd></div>
                <div><dt>Identidad</dt><dd>{current.customerRef ? 'Capturado del canal' : 'Pendiente'}</dd></div>
              </dl>
              {can('sales:manage') && <button type="button" className="link-button" onClick={() => { navigator.clipboard?.writeText(current.customerRef || ''); toast.success('Contacto copiado.'); }}>Copiar identificador completo</button>}
            </section>
            <section>
              <h3>Estado comercial</h3>
              <dl className="wa-data-list">
                <div><dt>Pedido</dt><dd>{statusLabel('whatsapp', current.status)}</dd></div>
                <div><dt>Pago</dt><dd>{current.paymentReference ? `${current.paymentMethod || 'Registrado'} · ${current.paymentReference}` : 'Pendiente de conciliación'}</dd></div>
                <div><dt>Creado</dt><dd>{dateTime(current.createdAt)}</dd></div>
                <div><dt>Actualizado</dt><dd>{dateTime(current.updatedAt)}</dd></div>
              </dl>
            </section>
          </div>
          <section className="wa-items-section">
            <div className="inventory-section-heading"><div><span>Última cotización vigente</span><h3>Pedido y artículos</h3></div><strong>{money(current.total)}</strong></div>
            <div className="wa-items">
              {listFrom(current.items).map(item => <div key={item.productId || item.sku}>
                <span><strong>{item.productName}</strong><small>{item.sku} · {number(item.quantity)} × {money(item.unitPrice)}</small></span>
                <strong>{money(item.lineTotal)}</strong>
              </div>)}
            </div>
            <dl className="wa-totals">
              <div><dt>Subtotal</dt><dd>{money(current.subtotal)}</dd></div>
              <div><dt>Impuestos</dt><dd>{money(current.tax)}</dd></div>
              <div><dt>Total</dt><dd>{money(current.total)}</dd></div>
            </dl>
          </section>
          <section>
            <h3>Seguimiento y trazabilidad</h3>
            <div className="wa-timeline">
              <div><span>Pedido recibido</span><small>{dateTime(current.createdAt)}</small></div>
              {current.confirmedAt && <div><span>Pago confirmado por {current.confirmedBy || 'personal autorizado'}</span><small>{dateTime(current.confirmedAt)}</small></div>}
              {current.saleId && <div><span>Venta vinculada</span><small>{current.saleId}</small></div>}
              {current.expiresAt && awaiting && <div><span>Validez de cotización</span><small>{relativeTime(current.expiresAt)}</small></div>}
            </div>
          </section>
        </>}
      </div>}
    </article>
    {paying && <ConfirmPayment order={current} onClose={() => setPaying(false)} onConfirmed={confirmed => {
      setDetail(confirmed); setPaying(false); onChanged();
    }} />}
  </>;
}

export default function WhatsAppOrdersPage({ can, onChanged }) {
  const [status, setStatus] = useState('AWAITING_PAYMENT');
  const [query, setQuery] = useState('');
  const [customer, setCustomer] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ loading: true, rows: [], total: 0, pages: 1 });
  const [summary, setSummary] = useState({ awaitingReview: 0, toPrepare: 0, expiringPayments: 0, incidents: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const [expandedId, setExpandedId] = useState(() => window.sessionStorage.getItem('wa-expanded-order') || '');

  useEffect(() => {
    const timer = window.setTimeout(() => setCustomer(query.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [query]);
  useEffect(() => { setPage(0); }, [status, customer]);

  const loadList = useCallback(async () => {
    setResult(current => ({ ...current, loading: true }));
    const params = { page, size: PAGE_SIZE, sort: 'createdAt,desc' };
    if (status) params.status = status;
    if (customer) params.customerRef = customer;
    try {
      const payload = await api.whatsappOrders(params);
      const meta = payload?.page || payload || {};
      setResult({ loading: false, rows: listFrom(payload), total: Number(meta.totalElements || 0), pages: Math.max(1, Number(meta.totalPages || 1)) });
    } catch (error) {
      setResult({ loading: false, rows: [], total: 0, pages: 1 });
      toast.error(error?.message || 'No se pudieron cargar los pedidos.');
    }
  }, [status, customer, page, reloadKey]);

  useEffect(() => { void loadList(); }, [loadList]);
  useEffect(() => {
    let active = true;
    api.whatsappWorkSummary().then(value => { if (active) setSummary(value); }).catch(() => {});
    return () => { active = false; };
  }, [reloadKey]);

  const refresh = useCallback(() => { setReloadKey(key => key + 1); onChanged?.(); void loadList(); }, [loadList, onChanged]);

  const metrics = useMemo(() => [
    ['Por revisar', summary.awaitingReview, () => setStatus('AWAITING_PAYMENT')],
    ['Por preparar', summary.toPrepare, () => setStatus('AWAITING_PAYMENT')],
    ['Pagos próximos a vencer', summary.expiringPayments, () => setStatus('AWAITING_PAYMENT')],
    ['Incidencias abiertas', summary.incidents, () => setStatus('')]
  ], [summary]);

  function toggle(id) {
    const next = expandedId === id ? '' : id;
    setExpandedId(next);
    if (next) window.sessionStorage.setItem('wa-expanded-order', next);
    else window.sessionStorage.removeItem('wa-expanded-order');
  }

  return <div className="page wa-orders-page">
    <header className="wa-page-heading"><div><p className="eyebrow">VENTAS CONVERSACIONALES</p><h2>Pedidos WhatsApp</h2><span>Recepción, cobro y seguimiento desde una sola bandeja.</span></div>
      <button type="button" className="button secondary" onClick={refresh}><RefreshCw size={16} /> Actualizar</button>
    </header>
    <div className="wa-metrics">
      {metrics.map(([label, value, action]) => <button type="button" key={label} onClick={action}><strong>{number(value)}</strong><span>{label}</span></button>)}
    </div>
    <div className="filter-bar wa-filter-bar">
      <div className="segmented" role="group" aria-label="Cola de pedidos">
        {QUEUES.map(([value, label]) => <button key={value || 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>)}
      </div>
      <label className="search-field"><Search size={16} /><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Número o número de WhatsApp" aria-label="Buscar pedido o cliente" /></label>
    </div>
    {result.loading && !result.rows.length ? <SkeletonTable rows={6} columns={6} label="Cargando pedidos" /> :
      <div className={'wa-order-list ' + (result.loading ? 'is-refreshing' : '')}>
        {result.rows.map(order => <OrderAccordion key={order.id} order={order} expanded={expandedId === order.id} onToggle={() => toggle(order.id)} can={can} onChanged={refresh} />)}
        {!result.rows.length && <div className="empty-block"><MessageCircle size={28} /><strong>Sin pedidos</strong><span>Los pedidos que lleguen por WhatsApp aparecerán aquí.</span></div>}
      </div>}
    {result.pages > 1 && <div className="pagination">
      <span>Página {page + 1} de {result.pages} · {number(result.total)} pedidos</span>
      <button type="button" className="icon-button" aria-label="Página anterior" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /></button>
      <button type="button" className="icon-button" aria-label="Página siguiente" disabled={page >= result.pages - 1} onClick={() => setPage(page + 1)}><ChevronRight size={16} /></button>
    </div>}
  </div>;
}

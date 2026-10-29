import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Ban, CalendarClock, ChevronLeft, ChevronRight, CircleDollarSign, Plus, Repeat } from 'lucide-react';
import { api, listFrom } from '../../api';
import Drawer from '../../ui/Drawer.jsx';
import { SkeletonTable } from '../../ui/Skeleton.jsx';
import { toast } from '../../ui/feedback.js';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';
import { calendarDate, isoDate, money, number, statusLabel, statusTone } from '../../utils/format.js';
import '../catalog/catalog.css';

const PAGE_SIZE = 20;
const STATUSES = [['ACTIVE', 'Activas'], ['CANCELLED', 'Canceladas'], ['COMPLETED', 'Finalizadas'], ['', 'Todas']];

function isActive(item) { return item?.active !== false && item?.status !== 'INACTIVE'; }
function termLabel(options, months) { return options?.terms?.find(term => term.months === Number(months))?.label || `${months} meses`; }

/** Opciones (frecuencias, duraciones y si el usuario puede dar descuentos) que define el backend. */
function useSubscriptionOptions() {
  const [options, setOptions] = useState(null);
  useEffect(() => {
    let active = true;
    api.subscriptionOptions().then(result => { if (active) setOptions(result); }).catch(() => { if (active) setOptions({ frequencies: [], terms: [], canDiscount: false }); });
    return () => { active = false; };
  }, []);
  return options;
}

function Modal({ titleId, title, description, busy, onClose, onSubmit, children, footer, wide }) {
  const dialogRef = useAccessibleDialog(true, () => { if (!busy) onClose(); });
  return createPortal(<div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <form ref={dialogRef} className={'modal-card subscription-modal' + (wide ? ' wide' : '')} role="dialog" aria-modal="true" aria-labelledby={titleId} onSubmit={onSubmit} noValidate tabIndex={-1}>
      <div className="modal-heading"><div><h2 id={titleId}>{title}</h2>{description && <p className="quick-create-description">{description}</p>}</div></div>
      {children}
      <div className="form-actions">{footer}</div>
    </form>
  </div>, document.body);
}

function NewSubscriptionModal({ options, products, customers, warehouses, onClose, onCreated }) {
  const services = useMemo(() => products.filter(item => isActive(item) && item.kind === 'SERVICE' && item.serviceBilling === 'SUBSCRIPTION'), [products]);
  const activeCustomers = useMemo(() => customers.filter(isActive), [customers]);
  const activeWarehouses = useMemo(() => warehouses.filter(isActive), [warehouses]);
  const [form, setForm] = useState(() => ({
    productId: '', customerId: '', warehouseId: activeWarehouses[0]?.id || '', startDate: isoDate(),
    billingFrequency: '', termMonths: '', monthlyPrice: '', prepay: false, discountPercent: '0'
  }));
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const service = services.find(item => String(item.id) === String(form.productId));

  function change(field, value) { setForm(current => ({ ...current, [field]: value })); }
  function chooseService(productId) {
    const selected = services.find(item => String(item.id) === String(productId));
    setForm(current => ({ ...current, productId, billingFrequency: selected?.billingFrequency || '', termMonths: String(selected?.termMonths || ''),
      monthlyPrice: selected ? String(selected.salePrice ?? '') : '' }));
  }

  useEffect(() => {
    const price = Number(form.monthlyPrice);
    if (!form.billingFrequency || !form.termMonths || String(form.monthlyPrice).trim() === '' || !Number.isFinite(price) || price < 0) { setPreview(null); return undefined; }
    let active = true;
    const timer = setTimeout(() => {
      api.subscriptionPreview({ billingFrequency: form.billingFrequency, termMonths: Number(form.termMonths), monthlyPrice: price, startDate: form.startDate || undefined })
        .then(result => { if (active) setPreview(result); })
        .catch(() => { if (active) setPreview(null); });
    }, 300);
    return () => { active = false; clearTimeout(timer); };
  }, [form.billingFrequency, form.termMonths, form.monthlyPrice, form.startDate]);

  async function submit(event) {
    event.preventDefault();
    if (!form.productId || !form.customerId || !form.warehouseId) { toast.error('Elige el servicio, el cliente y el local que factura.'); return; }
    const price = Number(form.monthlyPrice);
    if (!Number.isFinite(price) || price < 0) { toast.error('Escribe un precio mensual válido.'); return; }
    const discount = Number(form.discountPercent || 0);
    if (form.prepay && (!Number.isFinite(discount) || discount < 0 || discount > 100)) { toast.error('El descuento debe estar entre 0 y 100 %.'); return; }
    setBusy(true);
    try {
      const created = await api.createSubscription({
        productId: form.productId, customerId: form.customerId, warehouseId: form.warehouseId, startDate: form.startDate,
        billingFrequency: form.billingFrequency, termMonths: Number(form.termMonths), monthlyPrice: price,
        prepay: form.prepay, discountPercent: form.prepay && discount > 0 ? discount : undefined
      });
      toast.success(`Suscripción ${created.subscriptionNumber} creada.`);
      onCreated(created);
    } catch (error) {
      toast.error(error?.message || 'No se pudo crear la suscripción.');
      setBusy(false);
    }
  }

  const prepayTotal = preview ? Number(preview.total) * (1 - Number(form.discountPercent || 0) / 100) : 0;
  return <Modal titleId="subscription-new-title" title="Nueva suscripción" wide busy={busy} onClose={onClose} onSubmit={submit}
    description="El sistema genera solo cada factura en su fecha de cobro. Si el cliente cancela, solo paga el periodo vigente."
    footer={<>
      <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancelar</button>
      <button type="submit" className="button primary" disabled={busy || !services.length}><Plus size={16} />{busy ? 'Creando…' : 'Crear suscripción'}</button>
    </>}>
    {!services.length && <p className="form-hint">Aún no hay servicios por suscripción. Créalo en Productos eligiendo «Servicio» y luego «Por suscripción».</p>}
    <div className="form-grid">
      <label className="field"><span>Servicio<b> *</b></span>
        <select value={form.productId} onChange={event => chooseService(event.target.value)}>
          <option value="">Seleccionar servicio</option>
          {services.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label className="field"><span>Cliente<b> *</b></span>
        <select value={form.customerId} onChange={event => change('customerId', event.target.value)}>
          <option value="">Seleccionar cliente</option>
          {activeCustomers.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
        {!activeCustomers.length && <small className="field-help">Registra el cliente en Almacenes y contactos.</small>}
      </label>
      <label className="field"><span>Local que factura<b> *</b></span>
        <select value={form.warehouseId} onChange={event => change('warehouseId', event.target.value)}>
          {activeWarehouses.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label className="field"><span>Empieza el<b> *</b></span>
        <input type="date" min={isoDate()} value={form.startDate} onChange={event => change('startDate', event.target.value)} />
      </label>
      {service && <>
        <div className="field wide"><span>¿Cada cuánto se cobra?</span>
          <div className="type-grid">{(options?.frequencies || []).map(item => <button type="button" key={item.code} aria-pressed={form.billingFrequency === item.code}
            className={'type-card' + (form.billingFrequency === item.code ? ' selected' : '')} onClick={() => change('billingFrequency', item.code)}>
            <strong>{item.label}</strong><small>{item.description}</small></button>)}</div>
        </div>
        <div className="field"><span>Duración del plan</span>
          <div className="option-chips">{(options?.terms || []).map(term => <button type="button" key={term.months} aria-pressed={Number(form.termMonths) === term.months}
            className={'option-chip' + (Number(form.termMonths) === term.months ? ' selected' : '')} onClick={() => change('termMonths', String(term.months))}>{term.label}</button>)}</div>
        </div>
        <label className="field"><span>Precio por mes<b> *</b></span>
          <input type="number" min="0" step="0.01" inputMode="decimal" value={form.monthlyPrice} onChange={event => change('monthlyPrice', event.target.value)} />
        </label>
      </>}
    </div>
    {preview && <div className="subscription-preview">
      <strong>{preview.summary}</strong>
      <small>Vigencia: {calendarDate(form.startDate)} al {calendarDate(preview.endDate)}</small>
      <ol>{listFrom(preview.installments).slice(0, 6).map(item => <li key={item.sequence}><span>Cobro {item.sequence} · {calendarDate(item.billingDate)}</span><strong>{money(item.amount)}</strong></li>)}
        {listFrom(preview.installments).length > 6 && <li className="muted-row"><span>… y {listFrom(preview.installments).length - 6} cobros más</span></li>}</ol>
    </div>}
    {service && <div className="prepay-box">
      <label className="checkbox-field property-required">
        <input type="checkbox" checked={form.prepay} onChange={event => change('prepay', event.target.checked)} />
        <span>Cobrar todo por adelantado<small>Se genera una sola factura ahora por todo el plan.</small></span>
      </label>
      {form.prepay && (options?.canDiscount
        ? <label className="field"><span>Descuento por pago adelantado (%)</span>
          <input type="number" min="0" max="100" step="0.01" value={form.discountPercent} onChange={event => change('discountPercent', event.target.value)} />
          {preview && <small className="field-help">Se facturará {money(prepayTotal)} en lugar de {money(preview.total)}.</small>}
        </label>
        : <p className="form-hint">Sin descuento. Solo el propietario puede aplicar descuentos por pago adelantado.</p>)}
    </div>}
  </Modal>;
}

function CancelModal({ subscription, onClose, onDone }) {
  const [preview, setPreview] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api.subscriptionCancellationPreview(subscription.id).then(result => { if (active) setPreview(result); })
      .catch(error => { toast.error(error?.message || 'No se pudo calcular la cancelación.'); onClose(); });
    return () => { active = false; };
  }, [subscription.id]);
  async function submit(event) {
    event.preventDefault();
    if (!reason.trim()) { toast.error('Escribe el motivo de la cancelación.'); return; }
    setBusy(true);
    try {
      const updated = await api.cancelSubscription(subscription.id, reason.trim());
      toast.success('Suscripción cancelada.');
      onDone(updated);
    } catch (error) {
      toast.error(error?.message || 'No se pudo cancelar la suscripción.');
      setBusy(false);
    }
  }
  return <Modal titleId="subscription-cancel-title" title="Cancelar suscripción" busy={busy} onClose={onClose} onSubmit={submit}
    description={`${subscription.subscriptionNumber} · ${subscription.customerName}`}
    footer={<>
      <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Volver</button>
      <button type="submit" className="button primary danger" disabled={busy || !preview}><Ban size={16} />{busy ? 'Cancelando…' : 'Confirmar cancelación'}</button>
    </>}>
    {!preview ? <p className="form-hint">Calculando qué se cobrará…</p> : <div className="suggestion-note" role="status"><CalendarClock size={16} /><div><p>{preview.message}</p></div></div>}
    <label className="field"><span>Motivo<b> *</b></span>
      <textarea rows="2" maxLength="1000" value={reason} onChange={event => setReason(event.target.value)} placeholder="Ej. El cliente ya no necesita el servicio" />
    </label>
  </Modal>;
}

function PrepayModal({ subscription, options, onClose, onDone }) {
  const pending = listFrom(subscription.installments).filter(item => item.status === 'SCHEDULED');
  const gross = pending.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const [discount, setDiscount] = useState('0');
  const [busy, setBusy] = useState(false);
  const value = Number(discount || 0);
  async function submit(event) {
    event.preventDefault();
    if (!Number.isFinite(value) || value < 0 || value > 100) { toast.error('El descuento debe estar entre 0 y 100 %.'); return; }
    setBusy(true);
    try {
      const updated = await api.prepaySubscription(subscription.id, value > 0 ? value : undefined);
      toast.success('Se generó la factura por los cobros pendientes.');
      onDone(updated);
    } catch (error) {
      toast.error(error?.message || 'No se pudo registrar el pago adelantado.');
      setBusy(false);
    }
  }
  return <Modal titleId="subscription-prepay-title" title="Pagar por adelantado" busy={busy} onClose={onClose} onSubmit={submit}
    description={`Se facturan ahora los ${pending.length} cobros pendientes en una sola factura.`}
    footer={<>
      <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Volver</button>
      <button type="submit" className="button primary" disabled={busy || !pending.length}><CircleDollarSign size={16} />{busy ? 'Generando…' : 'Generar factura'}</button>
    </>}>
    <dl className="metric-grid">
      <div><dt>Pendiente</dt><dd>{money(gross)}</dd></div>
      <div><dt>Descuento</dt><dd>{number(value)} %</dd></div>
      <div><dt>A facturar</dt><dd>{money(gross * (1 - value / 100))}</dd></div>
    </dl>
    {options?.canDiscount
      ? <label className="field"><span>Descuento (%)</span><input type="number" min="0" max="100" step="0.01" value={discount} onChange={event => setDiscount(event.target.value)} /></label>
      : <p className="form-hint">Sin descuento. Solo el propietario puede aplicar descuentos por pago adelantado.</p>}
  </Modal>;
}

function SubscriptionDrawer({ id, can, options, onClose, onChanged }) {
  const [subscription, setSubscription] = useState(null);
  const [action, setAction] = useState('');
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    let active = true;
    setSubscription(null);
    api.subscription(id).then(detail => { if (active) setSubscription(detail); })
      .catch(error => { if (active) { toast.error(error?.message || 'No se pudo cargar la suscripción.'); closeRef.current(); } });
    return () => { active = false; };
  }, [id]);
  function updated(next) { setAction(''); setSubscription(next); onChanged(); }
  const active = subscription?.status === 'ACTIVE';
  const hasPending = listFrom(subscription?.installments).some(item => item.status === 'SCHEDULED');
  return <>
    <Drawer open title={subscription ? subscription.subscriptionNumber : 'Suscripción'} subtitle={subscription ? `${subscription.productName} · ${subscription.customerName}` : 'Cargando…'} onClose={onClose}
      footer={subscription && active && can('sales:manage') && <>
        {hasPending && <button type="button" className="button secondary" onClick={() => setAction('prepay')}><CircleDollarSign size={16} />Pagar por adelantado</button>}
        <button type="button" className="button secondary danger-text" onClick={() => setAction('cancel')}><Ban size={16} />Cancelar suscripción</button>
      </>}>
      {!subscription ? <SkeletonTable rows={4} columns={3} label="Cargando suscripción" /> : <>
        <div className="detail-pills">
          <span className={'pill ' + statusTone(subscription.status)}>{statusLabel('subscription', subscription.status)}</span>
          <span className="pill neutral">{subscription.billingFrequencyLabel}</span>
          <span className="pill neutral">{termLabel(options, subscription.termMonths)}</span>
          {subscription.prepaid && <span className="pill info">Pagada por adelantado</span>}
        </div>
        {subscription.lastBillingError && <p className="inline-warning">{subscription.lastBillingError}</p>}
        <dl className="metric-grid">
          <div><dt>Precio por mes</dt><dd>{money(subscription.monthlyPrice)}</dd></div>
          <div><dt>Total del plan</dt><dd>{money(subscription.totalAmount)}</dd></div>
          <div><dt>Facturado</dt><dd>{money(subscription.invoicedAmount)}</dd></div>
          <div><dt>Vigencia</dt><dd>{calendarDate(subscription.startDate)} – {calendarDate(subscription.endDate)}</dd></div>
          <div><dt>Próximo cobro</dt><dd>{subscription.nextBillingDate ? `${calendarDate(subscription.nextBillingDate)} · ${money(subscription.nextAmount)}` : '—'}</dd></div>
          <div><dt>Local</dt><dd>{subscription.warehouseCode}</dd></div>
        </dl>
        {subscription.prepaid && <p className="drawer-text">Pago adelantado de {money(subscription.prepaidAmount)}{Number(subscription.prepaidDiscountPercent) > 0 ? ` con ${number(subscription.prepaidDiscountPercent)} % de descuento` : ''}.</p>}
        {subscription.status === 'CANCELLED' && <p className="drawer-text">Cancelada por {subscription.cancelledBy}: {subscription.cancellationReason}</p>}
        <h3 className="drawer-subtitle">Calendario de cobros</h3>
        <ul className="line-items">{listFrom(subscription.installments).map(item => <li key={item.id}>
          <span><strong>Cobro {item.sequence} · {calendarDate(item.billingDate)}</strong>
            <small>Periodo {calendarDate(item.periodStart)} – {calendarDate(item.periodEnd)}{item.saleNumber ? ` · Factura ${item.saleNumber}` : ''}</small></span>
          <span className="installment-amount"><strong>{money(item.amount)}</strong><span className={'pill ' + statusTone(item.status)}>{statusLabel('installment', item.status)}</span></span>
        </li>)}</ul>
      </>}
    </Drawer>
    {action === 'cancel' && subscription && <CancelModal subscription={subscription} onClose={() => setAction('')} onDone={updated} />}
    {action === 'prepay' && subscription && <PrepayModal subscription={subscription} options={options} onClose={() => setAction('')} onDone={updated} />}
  </>;
}

export default function SubscriptionsPage({ can, products, customers, warehouses, detailId, onOpenDetail, onCloseDetail, onChanged }) {
  const options = useSubscriptionOptions();
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [result, setResult] = useState({ loading: true, rows: [], total: 0, pages: 1 });
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => { setPage(0); }, [status]);

  useEffect(() => {
    let active = true;
    setResult(current => ({ ...current, loading: true }));
    const params = { page, size: PAGE_SIZE, sort: 'createdAt,desc' };
    if (status) params.status = status;
    api.subscriptions(params)
      .then(payload => {
        if (!active) return;
        const meta = payload?.page || payload || {};
        setResult({ loading: false, rows: listFrom(payload), total: Number(meta.totalElements || 0), pages: Math.max(1, Number(meta.totalPages || 1)) });
      })
      .catch(error => {
        if (!active) return;
        setResult({ loading: false, rows: [], total: 0, pages: 1 });
        toast.error(error?.message || 'No se pudieron cargar las suscripciones.');
      });
    return () => { active = false; };
  }, [status, page, reloadKey]);

  const refresh = useCallback(() => { setReloadKey(key => key + 1); onChanged?.(); }, [onChanged]);

  return <div className="page">
    <div className="filter-bar">
      <div className="segmented" role="group" aria-label="Estado de la suscripción">
        {STATUSES.map(([value, label]) => <button key={value || 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>)}
      </div>
      {can('sales:manage') && <div className="filter-actions"><button type="button" className="button primary" onClick={() => setCreating(true)}><Plus size={16} />Nueva suscripción</button></div>}
    </div>
    {result.loading && !result.rows.length ? <SkeletonTable rows={6} columns={6} label="Cargando suscripciones" /> : <div className={'table-wrap data-table' + (result.loading ? ' is-refreshing' : '')}>
      <table aria-label="Suscripciones">
        <thead><tr><th scope="col">Suscripción</th><th scope="col">Cliente</th><th scope="col">Servicio</th><th scope="col">Plan</th><th scope="col">Próximo cobro</th><th scope="col" className="num">Facturado / total</th><th scope="col">Estado</th></tr></thead>
        <tbody>{result.rows.map(item => <tr key={item.id} className="clickable" onClick={() => onOpenDetail(item.id)}>
          <td><button type="button" className="row-title" onClick={event => { event.stopPropagation(); onOpenDetail(item.id); }}><strong>{item.subscriptionNumber}</strong><small>Desde {calendarDate(item.startDate)}</small></button></td>
          <td>{item.customerName}</td>
          <td>{item.productName}</td>
          <td>{item.billingFrequencyLabel} · {termLabel(options, item.termMonths)}</td>
          <td>{item.nextBillingDate ? `${calendarDate(item.nextBillingDate)} · ${money(item.nextAmount)}` : '—'}{item.lastBillingError && <span className="cell-note stock-value empty">Error al facturar</span>}</td>
          <td className="num">{money(item.invoicedAmount)} / {money(item.totalAmount)}</td>
          <td><span className={'pill ' + statusTone(item.status)}>{statusLabel('subscription', item.status)}</span></td>
        </tr>)}</tbody>
      </table>
      {!result.rows.length && <div className="empty-block"><Repeat size={28} /><strong>Sin suscripciones</strong><span>Los servicios que se cobran cada 15 días, cada mes o al finalizar aparecerán aquí.</span></div>}
      {result.pages > 1 && <div className="pagination">
        <span>Página {page + 1} de {result.pages} · {number(result.total)} suscripciones</span>
        <button type="button" className="icon-button" aria-label="Página anterior" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /></button>
        <button type="button" className="icon-button" aria-label="Página siguiente" disabled={page >= result.pages - 1} onClick={() => setPage(page + 1)}><ChevronRight size={16} /></button>
      </div>}
    </div>}
    {creating && <NewSubscriptionModal options={options} products={products} customers={customers} warehouses={warehouses}
      onClose={() => setCreating(false)} onCreated={created => { setCreating(false); refresh(); onOpenDetail(created.id); }} />}
    {detailId && <SubscriptionDrawer id={detailId} can={can} options={options} onClose={onCloseDetail} onChanged={refresh} />}
  </div>;
}

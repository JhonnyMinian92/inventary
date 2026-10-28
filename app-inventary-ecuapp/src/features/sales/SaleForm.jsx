import { useEffect, useMemo, useState } from 'react';
import { useMessageToast } from '../../ui/feedback.js';
import { ArrowLeft, ArrowRight, ShoppingCart, Trash2 } from 'lucide-react';
import { api } from '../../api';
import StepProgress from '../../components/StepProgress.jsx';

const emptyLine = () => ({ productId: '', quantity: '1' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function labelOf(item) { return item?.name || item?.description || item?.code || idOf(item); }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }

export default function SaleForm({ products, warehouses, customers, balances, loading, onCancel, onSubmit, onError }) {
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  useMessageToast(validationMessage, setValidationMessage);
  const [warehouseId, setWarehouseId] = useState(warehouses.find(item => item.active !== false && item.status !== 'INACTIVE')?.id || '');
  const [customerId, setCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [lines, setLines] = useState([emptyLine()]);
  const [availabilityByProduct, setAvailabilityByProduct] = useState({});
  const [availabilityWarning, setAvailabilityWarning] = useState('');
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [availabilityRetry, setAvailabilityRetry] = useState(0);
  const selectedProductIds = useMemo(() => [...new Set(lines.map(line => line.productId).filter(Boolean))].sort().join(','), [lines]);
  const activeProducts = useMemo(() => products.filter(item => item.active !== false && item.status !== 'INACTIVE' && (item.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD'), [products]);
  const activeWarehouses = useMemo(() => warehouses.filter(item => item.active !== false && item.status !== 'INACTIVE'), [warehouses]);
  const activeCustomers = useMemo(() => customers.filter(item => item.active !== false && item.status !== 'INACTIVE'), [customers]);
  const selectedWarehouse = activeWarehouses.find(item => String(idOf(item)) === String(warehouseId));
  const selectedCustomer = activeCustomers.find(item => String(idOf(item)) === String(customerId));
  const totals = useMemo(() => lines.reduce((result, line) => {
    const product = activeProducts.find(item => String(idOf(item)) === String(line.productId));
    const qty = Number(line.quantity);
    result.quantity += Number.isFinite(qty) && qty > 0 ? qty : 0;
    result.amount += Number.isFinite(qty) && qty > 0 ? qty * Number(product?.salePrice || 0) : 0;
    return result;
  }, { quantity: 0, amount: 0 }), [lines, activeProducts]);

  useEffect(() => {
    if (!warehouseId || !selectedProductIds) {
      setAvailabilityByProduct({});
      setAvailabilityWarning('');
      setAvailabilityLoading(false);
      return undefined;
    }
    let active = true;
    const ids = selectedProductIds.split(',').filter(Boolean);
    setAvailabilityLoading(true);
    setAvailabilityWarning('');
    Promise.all(ids.map(async productId => {
      try {
        const result = await api.availableStock({ productId, warehouseId });
        const amount = Number(result?.available);
        return [productId, Number.isFinite(amount) ? amount : null];
      } catch {
        return [productId, null];
      }
    })).then(entries => {
      if (!active) return;
      const next = Object.fromEntries(entries.filter(([, value]) => value !== null));
      setAvailabilityByProduct(next);
      setAvailabilityWarning(entries.some(([, value]) => value === null) ? 'No pudimos comprobar algunas existencias en tiempo real. El sistema volverá a validarlas al registrar la venta.' : '');
    }).finally(() => {
      if (active) setAvailabilityLoading(false);
    });
    return () => { active = false; };
  }, [warehouseId, selectedProductIds, availabilityRetry]);

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
    setValidationMessage('');
  }

  function stockFor(productId) {
    if (Object.prototype.hasOwnProperty.call(availabilityByProduct, String(productId))) {
      return { value: availabilityByProduct[String(productId)], verified: true };
    }
    const matching = balances.filter(balance => String(balance.productId || balance.product?.id) === String(productId) &&
      String(balance.warehouseId || balance.warehouse?.id) === String(warehouseId));
    if (matching.length) {
      return { value: matching.reduce((sum, balance) => sum + Number(balance.available ?? balance.onHand ?? 0), 0), verified: false };
    }
    return { value: null, verified: false };
  }

  function validateBasics() {
    if (!warehouseId || !selectedWarehouse) {
      setValidationMessage('Selecciona un almacén activo para registrar la salida.');
      return false;
    }
    if (customerId && !selectedCustomer) {
      setValidationMessage('El cliente seleccionado ya no está activo. Elige otro o usa una venta rápida.');
      return false;
    }
    return true;
  }

  function validateLines() {
    const items = lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity) }));
    if (!items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      setValidationMessage('Elige un producto y una cantidad mayor que cero en cada línea.');
      return false;
    }
    if (items.some(item => !activeProducts.some(product => String(idOf(product)) === String(item.productId)))) {
      setValidationMessage('Uno de los productos ya no está activo. Elige otro para continuar.');
      return false;
    }
    const ids = items.map(item => String(item.productId));
    if (new Set(ids).size !== ids.length) {
      setValidationMessage('Un producto solo puede aparecer una vez. Ajusta su cantidad en la misma línea.');
      return false;
    }
    const oversold = items.find(item => {
      const stock = stockFor(item.productId);
      return stock.value !== null && item.quantity > stock.value;
    });
    if (oversold) {
      const product = activeProducts.find(item => String(idOf(item)) === String(oversold.productId));
      const stock = stockFor(oversold.productId);
      setValidationMessage(`No hay existencias suficientes de "${product?.name || 'este producto'}". Disponible: ${stock.value}.`);
      return false;
    }
    return true;
  }

  function nextStep() {
    setValidationMessage('');
    if (step === 0 && !validateBasics()) return;
    if (step === 1 && !validateLines()) return;
    if (step === 1 && availabilityLoading) {
      setValidationMessage('Espera un momento mientras comprobamos las existencias.');
      return;
    }
    setStep(current => Math.min(2, current + 1));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function previousStep() {
    setValidationMessage('');
    setStep(current => Math.max(0, current - 1));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function submit(event) {
    event.preventDefault();
    setValidationMessage('');
    if (step === 0) {
      if (validateBasics()) setStep(1);
      return;
    }
    if (step === 1) {
      if (!validateLines()) return;
      if (availabilityLoading) {
        setValidationMessage('Espera un momento mientras comprobamos las existencias.');
        return;
      }
      setStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    if (!validateBasics()) { setStep(0); return; }
    if (!validateLines()) { setStep(1); return; }
    const unverified = lines.some(line => !stockFor(line.productId).verified);
    if (unverified && availabilityWarning) {
      setValidationMessage('No se pudo verificar todo el inventario. Revisa las existencias otra vez antes de confirmar.');
      setStep(1);
      return;
    }
    const data = { warehouseId, channel: 'POS', items: lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity) })) };
    if (customerId) data.customerId = customerId;
    else {
      if (customerName.trim()) data.customerName = customerName.trim();
      if (customerPhone.trim()) data.customerPhone = customerPhone.trim();
    }
    onSubmit(data);
  }

  return <form className="workflow-form" onSubmit={submit} noValidate>
    <StepProgress steps={['Datos de venta', 'Artículos', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />

    {step === 0 && <section className="workflow-stage" aria-labelledby="sale-stage-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><ShoppingCart size={20}/></span>
        <div><h2 id="sale-stage-title">Datos de la venta</h2><p>Indica de qué almacén saldrán los productos y quién compra.</p></div>
      </div>
      <div className="form-grid workflow-fields">
        <label className="field"><span>Almacén de salida<b> *</b></span>
          <select required value={warehouseId} onChange={event => { setWarehouseId(event.target.value); setValidationMessage(''); }}>
            <option value="">Seleccionar almacén</option>
            {activeWarehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)}{item.code ? ` · ${item.code}` : ''}</option>)}
          </select>
          {!activeWarehouses.length && <small className="field-help">No hay almacenes activos disponibles.</small>}
        </label>
        <label className="field"><span>Cliente <small>(opcional)</small></span>
          <select value={customerId} onChange={event => { setCustomerId(event.target.value); if (event.target.value) { setCustomerName(''); setCustomerPhone(''); } setValidationMessage(''); }}>
            <option value="">Venta rápida / sin cliente registrado</option>
            {activeCustomers.map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)}{item.code ? ` · ${item.code}` : ''}</option>)}
          </select>
        </label>
        {!customerId && <>
          <label className="field"><span>Nombre del cliente <small>(opcional)</small></span><input maxLength="160" value={customerName} onChange={event => setCustomerName(event.target.value)} placeholder="Nombre para el comprobante" /></label>
          <label className="field"><span>Teléfono <small>(opcional)</small></span><input type="tel" autoComplete="tel" maxLength="40" value={customerPhone} onChange={event => setCustomerPhone(event.target.value)} placeholder="Ej. 099 123 4567" /></label>
        </>}
      </div>
    </section>}

    {step === 1 && <section className="workflow-stage" aria-labelledby="sale-products-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><ShoppingCart size={20}/></span>
        <div><h2 id="sale-products-title">Artículos de la venta</h2><p>Solo aparecen productos de inventario activos. Revisa el stock antes de continuar.</p></div>
      </div>
      {!activeProducts.length && <div className="empty-state workflow-empty">No hay productos de inventario activos para vender.</div>}
      <div className="workflow-line-list">
        {lines.map((line, index) => {
          const product = activeProducts.find(item => String(idOf(item)) === String(line.productId));
          const stock = line.productId && warehouseId ? stockFor(line.productId) : { value: null, verified: false };
          const qty = Number(line.quantity);
          const qtyValid = Number.isFinite(qty) && qty > 0;
          return <section className="line-editor workflow-line" key={index} aria-label={`Artículo ${index + 1}`}>
            <div className="line-editor-heading"><strong>Artículo {index + 1}</strong>{lines.length > 1 && <button type="button" className="text-button danger-text" disabled={loading} onClick={() => { setLines(current => current.filter((_, lineIndex) => lineIndex !== index)); setValidationMessage(''); }}><Trash2 size={14}/> Quitar</button>}</div>
            <div className="form-grid compact-grid">
              <label className="field wide"><span>Producto<b> *</b></span>
                <select required value={line.productId} onChange={event => updateLine(index, 'productId', event.target.value)}>
                  <option value="">Seleccionar producto</option>
                  {activeProducts.filter(item => !lines.some((otherLine, otherIndex) => otherIndex !== index && String(otherLine.productId) === String(idOf(item)))).map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)} · {item.sku || item.code || idOf(item)}</option>)}
                </select>
              </label>
              <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" inputMode="decimal" required value={line.quantity} onChange={event => updateLine(index, 'quantity', event.target.value)} /></label>
              <div className={qtyValid && stock.value !== null && qty > stock.value ? 'stock-hint stock-hint-warning' : 'stock-hint'}>
                <small>Disponible {stock.verified ? '(actualizado)' : stock.value === null ? '(por verificar)' : '(último saldo)'}</small>
                <strong>{stock.value === null ? (availabilityLoading ? 'Comprobando…' : '—') : stock.value.toLocaleString('es-EC')}</strong>
                <small>Precio de catálogo: {product ? money(product.salePrice) : '—'}</small>
              </div>
            </div>
            {qtyValid && stock.value !== null && qty > stock.value && <p className="inline-warning">La cantidad supera las existencias mostradas. Reduce la cantidad para continuar.</p>}
          </section>;
        })}
      </div>
      {availabilityLoading && <p className="form-hint" role="status">Comprobando existencias…</p>}
      {availabilityWarning && <div className="inline-warning stock-retry" role="status"><span>{availabilityWarning}</span><button type="button" className="button secondary" disabled={availabilityLoading || loading} onClick={() => setAvailabilityRetry(value => value + 1)}>Volver a comprobar</button></div>}
      <div className="workflow-inline-actions"><button type="button" className="button secondary" disabled={loading || lines.length >= 100 || !activeProducts.some(item => !lines.some(line => String(line.productId) === String(idOf(item))))} onClick={() => { setLines(current => [...current, emptyLine()]); setValidationMessage(''); }}><ShoppingCart size={16}/> Añadir artículo</button><span className="workflow-inline-total"><small>Total estimado</small><strong>{money(totals.amount)}</strong></span></div>
    </section>}

    {step === 2 && <section className="workflow-stage" aria-labelledby="sale-review-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><ShoppingCart size={20}/></span>
        <div><h2 id="sale-review-title">Revisa antes de registrar</h2><p>Verifica el almacén, el cliente y los artículos.</p></div>
      </div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Almacén de salida</small><strong>{labelOf(selectedWarehouse)}</strong></div>
        <div className="workflow-review-card"><small>Cliente</small><strong>{selectedCustomer ? labelOf(selectedCustomer) : customerName.trim() || 'Venta rápida'}</strong></div>
        <div className="workflow-review-card"><small>Artículos diferentes</small><strong>{lines.length}</strong></div>
        <div className="workflow-review-card workflow-review-total"><small>Total estimado</small><strong>{money(totals.amount)}</strong></div>
      </div>
      <div className="workflow-review-lines">
        {lines.map((line, index) => {
          const product = activeProducts.find(item => String(idOf(item)) === String(line.productId));
          const qty = Number(line.quantity);
          return <div className="workflow-review-line" key={index}>
            <div><strong>{product?.name || 'Producto no seleccionado'}</strong><small>{line.quantity} × {money(product?.salePrice)}</small></div>
            <strong>{money((Number.isFinite(qty) ? qty : 0) * Number(product?.salePrice || 0))}</strong>
          </div>;
        })}
      </div>
      {availabilityWarning && <p className="inline-warning">{availabilityWarning}</p>}
      <p className="form-hint">El total es una estimación. El precio final y la disponibilidad se vuelven a validar al registrar.</p>
    </section>}

    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={previousStep}><ArrowLeft size={16}/> Atrás</button>}
        {/* Distinct keys keep React from turning the clicked "Continuar" node into the submit button mid-click,
            which would register the sale without showing the confirmation step. */}
        {step < 2 ? <button key="next" type="button" className="button primary" disabled={loading || (step === 1 && !activeProducts.length)} onClick={nextStep}>Continuar <ArrowRight size={16}/></button>
          : <button key="confirm" type="submit" className="button primary" disabled={loading || availabilityLoading}>{loading ? 'Registrando…' : 'Confirmar venta'}</button>}
      </div>
    </div>
  </form>;
}

import { useMemo, useState } from 'react';
import { ArrowLeft, ArrowRight, PackagePlus, Trash2 } from 'lucide-react';
import StepProgress from '../../components/StepProgress.jsx';

const emptyLine = () => ({ productId: '', quantity: '1', unitCost: '0' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function labelOf(item) { return item?.name || item?.description || item?.code || idOf(item); }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }

export default function PurchaseForm({ products, warehouses, suppliers, loading, initialValue, onCancel, onSubmit, onError }) {
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  const [warehouseId, setWarehouseId] = useState(initialValue?.warehouseId || warehouses.find(item => item.active !== false && item.status !== 'INACTIVE')?.id || '');
  const [supplierId, setSupplierId] = useState(initialValue?.supplierId || initialValue?.supplier?.id || '');
  const [notes, setNotes] = useState(initialValue?.notes || '');
  const [lines, setLines] = useState(() => {
    if (Array.isArray(initialValue?.items) && initialValue.items.length) {
      return initialValue.items.map(line => ({ productId: line.productId || line.product?.id || '', quantity: String(line.quantity ?? 1), unitCost: String(line.unitCost ?? line.costPrice ?? 0) }));
    }
    return [emptyLine()];
  });
  const activeWarehouses = useMemo(() => warehouses.filter(item => item.active !== false && item.status !== 'INACTIVE'), [warehouses]);
  const activeSuppliers = useMemo(() => suppliers.filter(item => item.active !== false && item.status !== 'INACTIVE'), [suppliers]);
  const activeProducts = useMemo(() => products.filter(item => item.active !== false && item.status !== 'INACTIVE' && (item.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD'), [products]);
  const total = useMemo(() => lines.reduce((sum, line) => sum + Math.max(0, Number(line.quantity || 0)) * Math.max(0, Number(line.unitCost || 0)), 0), [lines]);
  const selectedWarehouse = activeWarehouses.find(item => String(idOf(item)) === String(warehouseId));
  const selectedSupplier = activeSuppliers.find(item => String(idOf(item)) === String(supplierId));

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
    setValidationMessage('');
  }

  function validateBasics() {
    if (!warehouseId || !selectedWarehouse) {
      setValidationMessage('Selecciona un almacén activo para recibir la compra.');
      return false;
    }
    if (!supplierId || !selectedSupplier) {
      setValidationMessage('Selecciona un proveedor activo.');
      return false;
    }
    return true;
  }

  function normalizedItems() {
    return lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity), unitCost: Number(line.unitCost) }));
  }

  function validateLines() {
    const items = normalizedItems();
    if (!items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0 || !Number.isFinite(item.unitCost) || item.unitCost < 0)) {
      setValidationMessage('Completa cada línea con un producto, una cantidad mayor que cero y un costo válido.');
      return false;
    }
    if (items.some(item => !activeProducts.some(product => String(idOf(product)) === String(item.productId)))) {
      setValidationMessage('Uno de los productos ya no está activo. Elige otro producto para continuar.');
      return false;
    }
    const productIds = items.map(item => String(item.productId));
    if (new Set(productIds).size !== productIds.length) {
      setValidationMessage('Un producto solo puede aparecer una vez. Ajusta la cantidad en su línea.');
      return false;
    }
    if (items.some(item => !Number.isFinite(item.quantity * item.unitCost))) {
      setValidationMessage('El importe de una línea es demasiado grande. Revisa la cantidad y el costo.');
      return false;
    }
    return true;
  }

  function nextStep() {
    setValidationMessage('');
    if (step === 0 && !validateBasics()) return;
    if (step === 1 && !validateLines()) return;
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
    if (!validateBasics()) { setStep(0); return; }
    if (!validateLines()) { setStep(1); return; }
    if (step < 2) { nextStep(); return; }
    onSubmit({ warehouseId, supplierId, notes: notes.trim(), items: normalizedItems() });
  }

  return <form className="workflow-form" onSubmit={submit} noValidate>
    <StepProgress steps={['Datos básicos', 'Productos', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
    {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}

    {step === 0 && <section className="workflow-stage" aria-labelledby="purchase-stage-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><PackagePlus size={20}/></span>
        <div><h2 id="purchase-stage-title">¿De quién y dónde compras?</h2><p>Elige el proveedor y el almacén que recibirá los productos.</p></div>
      </div>
      <div className="form-grid workflow-fields">
        <label className="field"><span>Proveedor<b> *</b></span>
          <select required value={supplierId} onChange={event => { setSupplierId(event.target.value); setValidationMessage(''); }}>
            <option value="">Seleccionar proveedor</option>
            {activeSuppliers.map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)}{item.code ? ` · ${item.code}` : ''}</option>)}
          </select>
          {!activeSuppliers.length && <small className="field-help">No hay proveedores activos. Registra uno antes de continuar.</small>}
        </label>
        <label className="field"><span>Almacén de destino<b> *</b></span>
          <select required value={warehouseId} onChange={event => { setWarehouseId(event.target.value); setValidationMessage(''); }}>
            <option value="">Seleccionar almacén</option>
            {activeWarehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)}{item.code ? ` · ${item.code}` : ''}</option>)}
          </select>
          {!activeWarehouses.length && <small className="field-help">No hay almacenes activos disponibles.</small>}
        </label>
        <label className="field wide"><span>Notas <small>(opcional)</small></span><textarea rows="3" maxLength="1000" value={notes} onChange={event => setNotes(event.target.value)} placeholder="Número de factura, entrega u otra referencia" /></label>
      </div>
    </section>}

    {step === 1 && <section className="workflow-stage" aria-labelledby="purchase-products-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><PackagePlus size={20}/></span>
        <div><h2 id="purchase-products-title">¿Qué productos vas a comprar?</h2><p>Añade una línea por producto. Puedes ajustar cantidades y costos antes de confirmar.</p></div>
      </div>
      {!activeProducts.length && <div className="empty-state workflow-empty">No hay productos de inventario activos para comprar.</div>}
      <div className="workflow-line-list">
        {lines.map((line, index) => {
          const product = activeProducts.find(item => String(idOf(item)) === String(line.productId));
          const quantityValue = Number(line.quantity);
          const costValue = Number(line.unitCost);
          const validLine = line.productId && Number.isFinite(quantityValue) && quantityValue > 0 && Number.isFinite(costValue) && costValue >= 0;
          const lineTotal = validLine ? quantityValue * costValue : 0;
          return <section className="line-editor workflow-line" key={index} aria-label={`Producto ${index + 1}`}>
            <div className="line-editor-heading"><strong>Producto {index + 1}</strong>{lines.length > 1 && <button type="button" className="text-button danger-text" disabled={loading} onClick={() => { setLines(current => current.filter((_, lineIndex) => lineIndex !== index)); setValidationMessage(''); }}><Trash2 size={14}/> Quitar</button>}</div>
            <div className="form-grid compact-grid">
              <label className="field wide"><span>Producto<b> *</b></span>
                <select required value={line.productId} onChange={event => updateLine(index, 'productId', event.target.value)}>
                  <option value="">Seleccionar producto</option>
                  {activeProducts.filter(item => !lines.some((otherLine, otherIndex) => otherIndex !== index && String(otherLine.productId) === String(idOf(item)))).map(item => <option key={idOf(item)} value={idOf(item)}>{labelOf(item)} · {item.sku || item.code || idOf(item)}</option>)}
                </select>
              </label>
              <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" inputMode="decimal" required value={line.quantity} onChange={event => updateLine(index, 'quantity', event.target.value)} /></label>
              <label className="field"><span>Costo por unidad<b> *</b></span><input type="number" min="0" step="0.01" inputMode="decimal" required value={line.unitCost} onChange={event => updateLine(index, 'unitCost', event.target.value)} /></label>
            </div>
            <div className="line-editor-foot"><span>{product ? `Código: ${product.sku || product.code || '—'}` : 'Selecciona un producto'}</span><strong>{money(lineTotal)}</strong></div>
          </section>;
        })}
      </div>
      <div className="workflow-inline-actions"><button type="button" className="button secondary" disabled={loading || lines.length >= 100 || !activeProducts.some(item => !lines.some(line => String(line.productId) === String(idOf(item))))} onClick={() => { setLines(current => [...current, emptyLine()]); setValidationMessage(''); }}><PackagePlus size={16}/> Añadir producto</button><span className="workflow-inline-total"><small>Total estimado</small><strong>{money(total)}</strong></span></div>
    </section>}

    {step === 2 && <section className="workflow-stage" aria-labelledby="purchase-review-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><PackagePlus size={20}/></span>
        <div><h2 id="purchase-review-title">Revisa la compra</h2><p>Confirma que los datos y los importes estén correctos antes de crear la orden.</p></div>
      </div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Proveedor</small><strong>{labelOf(selectedSupplier)}</strong></div>
        <div className="workflow-review-card"><small>Almacén de destino</small><strong>{labelOf(selectedWarehouse)}</strong></div>
        <div className="workflow-review-card"><small>Productos diferentes</small><strong>{lines.length}</strong></div>
        <div className="workflow-review-card workflow-review-total"><small>Total estimado</small><strong>{money(total)}</strong></div>
      </div>
      <div className="workflow-review-lines">
        {lines.map((line, index) => {
          const product = activeProducts.find(item => String(idOf(item)) === String(line.productId));
          return <div className="workflow-review-line" key={index}>
            <div><strong>{product?.name || 'Producto no seleccionado'}</strong><small>{line.quantity} × {money(line.unitCost)}</small></div>
            <strong>{money(Number(line.quantity) * Number(line.unitCost))}</strong>
          </div>;
        })}
      </div>
      {notes.trim() && <p className="workflow-review-note"><strong>Notas:</strong> {notes.trim()}</p>}
      <p className="form-hint">La orden se crea como borrador. Envíala para aprobación; el inventario cambia al contabilizar la recepción.</p>
    </section>}

    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={previousStep}><ArrowLeft size={16}/> Atrás</button>}
        {step < 2 ? <button type="button" className="button primary" disabled={loading || (step === 1 && !activeProducts.length)} onClick={nextStep}>Continuar <ArrowRight size={16}/></button>
          : <button type="submit" className="button primary" disabled={loading}>{loading ? 'Creando orden…' : 'Confirmar compra'}</button>}
      </div>
    </div>
  </form>;
}

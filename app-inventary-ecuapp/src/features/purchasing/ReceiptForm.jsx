import { useMemo, useState } from 'react';
import { ArrowLeft, ArrowRight, PackageCheck } from 'lucide-react';
import StepProgress from '../../components/StepProgress.jsx';

function idOf(item) { return item?.id ?? item?.purchaseOrderLineId ?? ''; }
function lineName(line) { return line?.productName || line?.product?.name || line?.name || line?.sku || ('Producto ' + idOf(line)); }
function ordered(line) { return Number(line?.orderedQuantity ?? line?.quantityOrdered ?? line?.quantity ?? line?.requestedQuantity ?? 0); }
function received(line) { return Number(line?.receivedQuantity ?? line?.quantityReceived ?? line?.received ?? 0); }
function remaining(line) {
  const explicit = line?.remainingQuantity ?? line?.outstandingQuantity;
  return explicit == null ? Math.max(0, ordered(line) - received(line)) : Math.max(0, Number(explicit));
}

export default function ReceiptForm({ order, loading, onCancel, onSubmit }) {
  const orderLines = Array.isArray(order?.items) ? order.items : Array.isArray(order?.lines) ? order.lines : [];
  const [quantities, setQuantities] = useState(() => orderLines.map(() => '0'));
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  const pending = useMemo(() => orderLines.filter((line, index) => Number(quantities[index] || 0) > 0 && idOf(line)), [orderLines, quantities]);
  const totalUnits = pending.reduce((sum, line) => sum + Number(quantities[orderLines.indexOf(line)] || 0), 0);

  function getItems() {
    return orderLines.map((line, index) => ({
      purchaseOrderLineId: idOf(line),
      quantity: Number(quantities[index] || 0),
      remaining: remaining(line),
      name: lineName(line)
    }));
  }

  function validateQuantities() {
    if (!orderLines.length) {
      setValidationMessage('Esta orden no tiene líneas que se puedan recibir. Recarga la orden o consulta al administrador.');
      return false;
    }
    const items = getItems();
    const invalid = items.find(item => !Number.isFinite(item.quantity) || item.quantity < 0 || item.quantity > item.remaining);
    if (invalid) {
      setValidationMessage(`La cantidad de "${invalid.name}" debe estar entre cero y ${invalid.remaining}.`);
      return false;
    }
    if (!items.some(item => item.purchaseOrderLineId && item.quantity > 0)) {
      setValidationMessage('Indica la cantidad recibida de al menos un producto.');
      return false;
    }
    return true;
  }

  function handleSubmit(event) {
    event.preventDefault();
    setValidationMessage('');
    if (!validateQuantities()) return;
    if (step === 0) {
      setStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    onSubmit({ items: getItems().filter(item => item.purchaseOrderLineId && item.quantity > 0).map(({ purchaseOrderLineId, quantity }) => ({ purchaseOrderLineId, quantity })) });
  }

  function previousStep() {
    setValidationMessage('');
    setStep(0);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  return <form className="workflow-form" onSubmit={handleSubmit} noValidate>
    <StepProgress steps={['Cantidades recibidas', 'Confirmar recepción']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
    {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}

    <section className="workflow-stage" aria-labelledby="receipt-stage-title">
      <div className="workflow-stage-heading">
        <span className="workflow-stage-icon"><PackageCheck size={20}/></span>
        <div><h2 id="receipt-stage-title">{step === 0 ? '¿Qué llegó al almacén?' : 'Revisa la recepción'}</h2>
          <p>{step === 0 ? 'Anota solo la cantidad que recibiste físicamente. Puedes dejar en cero lo que no llegó.' : 'Solo se registrarán las cantidades mayores que cero que aparecen aquí.'}</p>
        </div>
      </div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Orden de compra</small><strong>{order?.number || order?.code || order?.id || '—'}</strong></div>
        <div className="workflow-review-card"><small>Productos con recepción</small><strong>{pending.length}</strong></div>
        <div className="workflow-review-card workflow-review-total"><small>Total de unidades</small><strong>{totalUnits.toLocaleString('es-EC')}</strong></div>
      </div>

      {step === 0 && <>
        {orderLines.length === 0 && <div className="alert error" role="alert">La orden no contiene líneas visibles. No se puede contabilizar una recepción vacía.</div>}
        <div className="workflow-line-list">
          {orderLines.map((line, index) => {
            const max = remaining(line);
            return <div className="receipt-line workflow-line" key={idOf(line) || index}>
              <div><strong>{lineName(line)}</strong><small>Solicitado: {ordered(line)} · Recibido: {received(line)} · Pendiente: {max}</small></div>
              <label className="field"><span>Cantidad recibida</span><input aria-label={`Cantidad recibida de ${lineName(line)}`} type="number" min="0" max={max} step="0.001" inputMode="decimal" value={quantities[index] ?? '0'} onChange={event => { setQuantities(current => current.map((value, qtyIndex) => qtyIndex === index ? event.target.value : value)); setValidationMessage(''); }} /></label>
            </div>;
          })}
        </div>
      </>}

      {step === 1 && <div className="workflow-review-lines">
        {pending.map(line => {
          const index = orderLines.indexOf(line);
          return <div className="workflow-review-line" key={idOf(line) || index}>
            <div><strong>{lineName(line)}</strong><small>Quedarán pendientes {Math.max(0, remaining(line) - Number(quantities[index] || 0))} unidades de esta orden.</small></div>
            <strong>{Number(quantities[index] || 0).toLocaleString('es-EC')}</strong>
          </div>;
        })}
        <p className="form-hint">Al confirmar, se actualizarán las existencias del almacén. Revisa las cantidades antes de continuar.</p>
      </div>}
    </section>

    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={previousStep}><ArrowLeft size={16}/> Atrás</button>}
        {step === 0 ? <button type="submit" className="button primary" disabled={loading || !orderLines.length}>Revisar <ArrowRight size={16}/></button>
          : <button type="submit" className="button primary" disabled={loading || !pending.length}>{loading ? 'Registrando…' : 'Confirmar recepción'}</button>}
      </div>
    </div>
  </form>;
}

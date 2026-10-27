import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, PackageMinus, X } from 'lucide-react';
import { api, listFrom, listAllPages } from '../../api';
import StepProgress from '../../components/StepProgress.jsx';

function idOf(item) { return item?.id ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }
function readPurchaseReturnFromUrl() {
  if (typeof window === 'undefined') return false;
  const parts = window.location.hash.slice(1).replace(/^\\/+/, '').split(/[?]/)[0].split('/').filter(Boolean);
  return parts[0] === 'purchases' && parts[1] === 'returns' && parts[2] === 'new';
}

export default function PurchaseReturns({ onChanged }) {
  const [returns, setReturns] = useState([]);
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [formOpen, setFormOpen] = useState(readPurchaseReturnFromUrl);
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  const [receiptId, setReceiptId] = useState('');
  const [receipt, setReceipt] = useState(null);
  const [lines, setLines] = useState([]);
  const [reason, setReason] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    const [returnResult, receiptResult] = await Promise.allSettled([
      listAllPages(api.purchaseReturns, { size: 100 }),
      listAllPages(api.goodsReceipts, { size: 100 })
    ]);
    const failures = [];
    if (returnResult.status === 'fulfilled') setReturns(listFrom(returnResult.value));
    else failures.push('devoluciones a proveedores: ' + (returnResult.reason?.message || 'error'));
    if (receiptResult.status === 'fulfilled') setReceipts(listFrom(receiptResult.value).filter(item => String(item.status || '').toUpperCase() === 'POSTED'));
    else failures.push('recepciones: ' + (receiptResult.reason?.message || 'error'));
    if (failures.length) setError('No se pudieron cargar todos los datos. ' + failures.join(' · '));
    setLoading(false);
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    const syncRoute = () => {
      const nextOpen = readPurchaseReturnFromUrl();
      const hasChanges = Boolean(receiptId || reason.trim() || lines.some(line => Number(line.quantity) > 0));
      if (!nextOpen && formOpen && hasChanges && !window.confirm('¿Salir de la devolución? Se perderán las cantidades que hayas escrito.')) {
        window.history.pushState({ workflow: 'purchase-return' }, '', '#/purchases/returns/new');
        return;
      }
      setFormOpen(nextOpen);
    };
    window.addEventListener('popstate', syncRoute);
    window.addEventListener('hashchange', syncRoute);
    return () => {
      window.removeEventListener('popstate', syncRoute);
      window.removeEventListener('hashchange', syncRoute);
    };
  }, [formOpen, receiptId, reason, lines]);

  useEffect(() => {
    if (!receiptId) {
      setReceipt(null);
      setLines([]);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([api.goodsReceipt(receiptId), listAllPages(api.purchaseReturns, { goodsReceiptId: receiptId, size: 100 })])
      .then(([receiptDetail, returnPayload]) => {
        if (!active) return;
        const priorReturns = listFrom(returnPayload).filter(item => String(item.status || '').toUpperCase() !== 'CANCELLED');
        const returnedByReceiptLine = new Map();
        priorReturns.forEach(ret => (Array.isArray(ret.items) ? ret.items : []).forEach(line => {
          const key = String(line.receiptLineId || '');
          if (key) returnedByReceiptLine.set(key, (returnedByReceiptLine.get(key) || 0) + Number(line.quantity || 0));
        }));
        const receiptLines = Array.isArray(receiptDetail.items) ? receiptDetail.items : [];
        setReceipt(receiptDetail);
        setLines(receiptLines.map(line => {
          const original = Number(line.quantity || 0);
          const alreadyReturned = returnedByReceiptLine.get(String(idOf(line))) || 0;
          return {
            receiptLineId: idOf(line),
            sku: line.sku || '',
            productId: line.productId || '',
            productName: line.productName || line.name || line.sku || 'Producto',
            quantity: '0',
            receivedQuantity: original,
            remainingQuantity: Math.max(0, original - alreadyReturned),
            unitCost: Number(line.unitCost || 0)
          };
        }));
      })
      .catch(err => {
        if (active) {
          setReceipt(null);
          setLines([]);
          setError('No se pudo cargar el detalle de la recepción: ' + (err?.message || 'error'));
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [receiptId]);

  function openForm() {
    setReceiptId('');
    setReceipt(null);
    setLines([]);
    setReason('');
    setStep(0);
    setValidationMessage('');
    setError('');
    setNotice('');
    setFormOpen(true);
    window.history.pushState({ workflow: 'purchase-return' }, '', '#/purchases/returns/new');
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function closeForm() {
    const hasChanges = Boolean(receiptId || reason.trim() || lines.some(line => Number(line.quantity) > 0));
    if (hasChanges && !window.confirm('¿Salir de la devolución? Se perderán las cantidades que hayas escrito.')) return;
    setFormOpen(false);
    setReceiptId('');
    setReceipt(null);
    setLines([]);
    setReason('');
    setStep(0);
    setValidationMessage('');
    setError('');
    window.history.replaceState({ section: 'purchases' }, '', '#/purchases');
  }

  function updateLine(index, quantity) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, quantity } : line));
    setValidationMessage('');
  }

  function validateReturn() {
    if (!receiptId || !receipt) {
      setValidationMessage('Selecciona una recepción contabilizada.');
      setStep(0);
      return null;
    }
    if (!reason.trim()) {
      setValidationMessage('Escribe el motivo de la devolución.');
      setStep(1);
      return null;
    }
    const invalidLine = lines.find(line => {
      const value = Number(line.quantity);
      return !Number.isFinite(value) || value < 0 || value > line.remainingQuantity;
    });
    if (invalidLine) {
      setValidationMessage(`La cantidad de "${invalidLine.productName}" debe estar entre cero y ${invalidLine.remainingQuantity}.`);
      setStep(1);
      return null;
    }
    const items = lines.filter(line => Number(line.quantity) > 0).map(line => ({ receiptLineId: line.receiptLineId, quantity: Number(line.quantity) }));
    if (!items.length || items.some(item => !item.receiptLineId || item.quantity <= 0)) {
      setValidationMessage('Añade una cantidad mayor que cero en al menos un producto.');
      setStep(1);
      return null;
    }
    return items;
  }

  async function submit(event) {
    event.preventDefault();
    setValidationMessage('');
    if (step === 0) {
      if (!receiptId) {
        setValidationMessage('Selecciona una recepción contabilizada para continuar.');
        return;
      }
      if (loading || !receipt) {
        setValidationMessage('Espera a que se carguen los artículos de la recepción.');
        return;
      }
      if (!lines.some(line => line.remainingQuantity > 0)) {
        setValidationMessage('Esta recepción no tiene cantidades pendientes de devolución.');
        return;
      }
      setStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    if (step === 1) {
      if (!validateReturn()) return;
      setStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const items = validateReturn();
    if (!items) return;
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await api.createPurchaseReturn({ goodsReceiptId: receiptId, reason: reason.trim(), items });
      setFormOpen(false);
      setReceiptId('');
      setReceipt(null);
      setLines([]);
      setReason('');
      setStep(0);
      window.history.replaceState({ section: 'purchases' }, '', '#/purchases');
      await refresh();
      await onChanged?.();
      setNotice('Devolución registrada. Se actualizó el inventario.');
    } catch (err) {
      setError(err?.message || 'No se pudo registrar la devolución al proveedor.');
    } finally {
      setLoading(false);
    }
  }

  const estimatedTotal = lines.reduce((sum, line) => sum + Math.max(0, Number(line.quantity || 0)) * line.unitCost, 0);
  const selectedReceipt = receipt && (receipt.receiptNumber || idOf(receipt));

  return <section className="returns-panel">
    <div className="panel-heading">
      <div><h3>Devoluciones a proveedores</h3><p>Registra mercancía devuelta desde una recepción contabilizada.</p></div>
      <button type="button" className="button primary" onClick={openForm} disabled={formOpen}>+ Nueva devolución</button>
    </div>
    {error && !formOpen && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success" role="status" aria-live="polite"><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
    {!formOpen && <div className="table-wrap"><table><thead><tr><th>Devolución</th><th>Orden</th><th>Recepción</th><th>Estado</th><th>Fecha</th><th>Motivo</th><th>Total</th></tr></thead><tbody>
      {returns.map((row,index) => <tr key={idOf(row)||index}>
        <td><strong>{row.returnNumber || idOf(row)}</strong></td><td>{row.purchaseOrderNumber || row.purchaseOrderId || '—'}</td>
        <td>{row.receiptNumber || row.receiptId || '—'}</td><td><span className={'pill ' + (row.status === 'POSTED' ? 'active' : 'inactive')}>{row.status || '—'}</span></td>
        <td>{date(row.createdAt)}</td><td>{row.reason || '—'}</td><td>{money(row.total)}</td>
      </tr>)}
    </tbody></table>{!returns.length && <div className="empty-state">{loading ? 'Cargando devoluciones…' : 'Todavía no hay devoluciones a proveedores.'}</div>}</div>}

    {formOpen && <section className="return-workspace" aria-labelledby="purchase-return-title">
      <div className="return-workspace-heading">
        <div><span className="eyebrow">DEVOLUCIÓN A PROVEEDOR</span><h3 id="purchase-return-title">Registrar devolución</h3><p>Selecciona la recepción, indica cantidades y confirma antes de contabilizar.</p></div>
        <button type="button" className="button secondary" disabled={loading} onClick={closeForm}><X size={16}/> Cancelar</button>
      </div>
      <StepProgress steps={['Recepción', 'Cantidades y motivo', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
      {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}
      {error && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
      <form className="workflow-form" onSubmit={submit} noValidate>
        {step === 0 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><PackageMinus size={20}/></span><div><h2>¿De qué recepción sale la devolución?</h2><p>Solo se muestran recepciones contabilizadas.</p></div></div>
          <label className="field"><span>Recepción<b> *</b></span><select required value={receiptId} disabled={loading} onChange={event => { setReceiptId(event.target.value); setValidationMessage(''); }}><option value="">Seleccionar recepción</option>{receipts.map(item => <option key={idOf(item)} value={idOf(item)}>{item.receiptNumber || idOf(item)} · {item.purchaseOrderNumber || 'Orden'} · {item.warehouseCode || item.warehouseName || 'Almacén'}</option>)}</select></label>
          {!receipts.length && <p className="form-hint">No hay recepciones contabilizadas disponibles para devolver.</p>}
          {receiptId && loading && <p className="form-hint" role="status">Cargando artículos y cantidades que todavía puedes devolver…</p>}
          {receipt && <div className="workflow-review-grid"><div className="workflow-review-card"><small>Recepción</small><strong>{selectedReceipt}</strong></div><div className="workflow-review-card"><small>Productos devueltos posibles</small><strong>{lines.filter(line => line.remainingQuantity > 0).length}</strong></div></div>}
        </section>}

        {step === 1 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><PackageMinus size={20}/></span><div><h2>¿Qué cantidad vas a devolver?</h2><p>No puedes devolver más unidades de las que se aceptaron y aún no se han devuelto.</p></div></div>
          {lines.length === 0 && <div className="empty-state">{loading ? 'Cargando artículos…' : 'No hay artículos para mostrar.'}</div>}
          <div className="workflow-line-list">{lines.map((line,index) => <section className="line-editor workflow-line" key={line.receiptLineId || index}>
            <div className="line-editor-heading"><strong>{line.productName} {line.sku ? '· ' + line.sku : ''}</strong><small>Recibido: {line.receivedQuantity} · Disponible para devolver: {line.remainingQuantity}</small></div>
            <div className="form-grid compact-grid"><label className="field"><span>Cantidad a devolver</span><input type="number" min="0" max={line.remainingQuantity} step="0.001" inputMode="decimal" value={line.quantity} disabled={loading || !line.remainingQuantity} onChange={event => updateLine(index,event.target.value)} /></label><div className="stock-hint"><small>Costo unitario de origen</small><strong>{money(line.unitCost)}</strong></div></div>
          </section>)}</div>
          <div className="workflow-inline-actions"><span className="workflow-inline-total"><small>Valor estimado</small><strong>{money(estimatedTotal)}</strong></span></div>
          <label className="field"><span>Motivo<b> *</b></span><textarea required maxLength="1000" rows="3" value={reason} onChange={event => { setReason(event.target.value); setValidationMessage(''); }} placeholder="Ej. Producto dañado o cantidad incorrecta" /></label>
        </section>}

        {step === 2 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><PackageMinus size={20}/></span><div><h2>Confirma la devolución</h2><p>Al confirmar, el inventario del almacén se reducirá según las cantidades seleccionadas.</p></div></div>
          <div className="workflow-review-grid"><div className="workflow-review-card"><small>Recepción</small><strong>{selectedReceipt || '—'}</strong></div><div className="workflow-review-card"><small>Motivo</small><strong>{reason.trim() || '—'}</strong></div><div className="workflow-review-card"><small>Productos</small><strong>{lines.filter(line => Number(line.quantity) > 0).length}</strong></div><div className="workflow-review-card workflow-review-total"><small>Total estimado</small><strong>{money(estimatedTotal)}</strong></div></div>
          <div className="workflow-review-lines">{lines.filter(line => Number(line.quantity) > 0).map(line => <div className="workflow-review-line" key={line.receiptLineId}><div><strong>{line.productName}</strong><small>{line.quantity} unidades · costo {money(line.unitCost)}</small></div><strong>{money(Number(line.quantity) * line.unitCost)}</strong></div>)}</div>
          <p className="form-hint">El backend comprobará las cantidades una última vez antes de actualizar el inventario.</p>
        </section>}

        <div className="form-actions workflow-actions">
          <button type="button" className="button secondary" disabled={loading} onClick={closeForm}>Cancelar</button>
          <div className="workflow-actions-right">
            {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={() => { setValidationMessage(''); setStep(value => Math.max(0, value - 1)); }}><ArrowLeft size={16}/> Atrás</button>}
            {step < 2 ? <button type="submit" className="button primary" disabled={loading || (step === 0 && !receipts.length)}>{step === 0 ? 'Continuar' : 'Revisar devolución'} <ArrowRight size={16}/></button>
              : <button type="submit" className="button primary" disabled={loading || !receipt || !reason.trim() || !lines.some(line => Number(line.quantity) > 0)}>{loading ? 'Registrando…' : 'Confirmar devolución'}</button>}
          </div>
        </div>
      </form>
    </section>}
  </section>;
}

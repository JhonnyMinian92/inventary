import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, ArrowRight, RotateCcw, X } from 'lucide-react';
import { api, listFrom, listAllPages } from '../../api';
import StepProgress from '../../components/StepProgress.jsx';

function idOf(value) { return value?.id ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }
function readSalesReturnFromUrl() {
  if (typeof window === 'undefined') return false;
  const parts = window.location.hash.slice(1).replace(/^\\/+/, '').split(/[?]/)[0].split('/').filter(Boolean);
  return parts[0] === 'sales' && parts[1] === 'returns' && parts[2] === 'new';
}

export default function SalesReturns({ onChanged }) {
  const [rows, setRows] = useState([]);
  const [sales, setSales] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [formOpen, setFormOpen] = useState(readSalesReturnFromUrl);
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  const [sourceSaleId, setSourceSaleId] = useState('');
  const [sourceSale, setSourceSale] = useState(null);
  const [lines, setLines] = useState([]);
  const [reason, setReason] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    const [returnsResult, salesResult] = await Promise.allSettled([
      listAllPages(api.salesReturns, { size: 100, sort: 'createdAt,desc' }),
      listAllPages(api.sales, { size: 100, sort: 'soldAt,desc' })
    ]);
    const failures = [];
    if (returnsResult.status === 'fulfilled') setRows(listFrom(returnsResult.value));
    else failures.push('devoluciones: ' + (returnsResult.reason?.message || 'error'));
    if (salesResult.status === 'fulfilled') setSales(listFrom(salesResult.value).filter(sale => String(sale.status || '').toUpperCase() === 'COMPLETED'));
    else failures.push('ventas: ' + (salesResult.reason?.message || 'error'));
    if (failures.length) setError('No se pudieron cargar todos los datos. ' + failures.join(' · '));
    setLoading(false);
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    const syncRoute = () => {
      const nextOpen = readSalesReturnFromUrl();
      const hasChanges = Boolean(sourceSaleId || reason.trim() || lines.some(line => Number(line.quantity) > 0));
      if (!nextOpen && formOpen && hasChanges && !window.confirm('¿Salir de la devolución? Se perderán las cantidades que hayas escrito.')) {
        window.history.pushState({ workflow: 'sales-return' }, '', '#/sales/returns/new');
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
  }, [formOpen, sourceSaleId, reason, lines]);

  useEffect(() => {
    if (!sourceSaleId) {
      setSourceSale(null);
      setLines([]);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([api.sale(sourceSaleId), listAllPages(api.salesReturns, { sourceSaleId, size: 100 })])
      .then(([sale, returnsPayload]) => {
        if (!active) return;
        const returns = listFrom(returnsPayload).filter(item => String(item.status || '').toUpperCase() !== 'CANCELLED');
        const returnedByItem = new Map();
        returns.forEach(ret => (Array.isArray(ret.items) ? ret.items : []).forEach(line => {
          const key = String(line.saleItemId || '');
          if (key) returnedByItem.set(key, (returnedByItem.get(key) || 0) + Number(line.quantity || 0));
        }));
        const saleLines = Array.isArray(sale.items) ? sale.items : [];
        setSourceSale(sale);
        setLines(saleLines.map(line => {
          const original = Number(line.quantity || 0);
          const previouslyReturned = returnedByItem.get(String(idOf(line))) || 0;
          return {
            saleItemId: idOf(line),
            sku: line.sku || '',
            productName: line.productName || line.name || line.sku || 'Producto',
            originalQuantity: original,
            remainingQuantity: Math.max(0, original - previouslyReturned),
            quantity: '0',
            disposition: 'RESTOCK',
            unitPrice: Number(line.unitPrice || 0)
          };
        }));
      })
      .catch(err => {
        if (active) {
          setSourceSale(null);
          setLines([]);
          setError('No se pudieron cargar los artículos de la venta: ' + (err?.message || 'error'));
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [sourceSaleId]);

  const returnTotal = useMemo(() => lines.reduce((sum, line) => sum + Math.max(0, Number(line.quantity || 0)) * line.unitPrice, 0), [lines]);

  function openForm() {
    setSourceSaleId('');
    setSourceSale(null);
    setLines([]);
    setReason('');
    setStep(0);
    setValidationMessage('');
    setError('');
    setNotice('');
    setFormOpen(true);
    window.history.pushState({ workflow: 'sales-return' }, '', '#/sales/returns/new');
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function closeForm() {
    const hasChanges = Boolean(sourceSaleId || reason.trim() || lines.some(line => Number(line.quantity) > 0));
    if (hasChanges && !window.confirm('¿Salir de la devolución? Se perderán las cantidades que hayas escrito.')) return;
    setFormOpen(false);
    setSourceSaleId('');
    setSourceSale(null);
    setLines([]);
    setReason('');
    setStep(0);
    setValidationMessage('');
    setError('');
    window.history.replaceState({ section: 'sales' }, '', '#/sales');
  }

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
    setValidationMessage('');
  }

  function validateReturn() {
    if (!sourceSaleId || !sourceSale) {
      setValidationMessage('Selecciona una venta completada.');
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
    const items = lines.filter(line => Number(line.quantity) > 0).map(line => ({ saleItemId: line.saleItemId, quantity: Number(line.quantity), disposition: line.disposition }));
    if (!items.length || items.some(item => !item.saleItemId || item.quantity <= 0)) {
      setValidationMessage('Añade una cantidad mayor que cero en al menos un artículo.');
      setStep(1);
      return null;
    }
    return items;
  }

  async function submit(event) {
    event.preventDefault();
    setValidationMessage('');
    if (step === 0) {
      if (!sourceSaleId) {
        setValidationMessage('Selecciona una venta completada para continuar.');
        return;
      }
      if (loading || !sourceSale) {
        setValidationMessage('Espera a que se carguen los artículos de la venta.');
        return;
      }
      if (!lines.some(line => line.remainingQuantity > 0)) {
        setValidationMessage('Esta venta no tiene cantidades pendientes de devolución.');
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
      await api.createSaleReturn({ sourceSaleId, reason: reason.trim(), items });
      setFormOpen(false);
      setSourceSaleId('');
      setSourceSale(null);
      setLines([]);
      setReason('');
      setStep(0);
      window.history.replaceState({ section: 'sales' }, '', '#/sales');
      await refresh();
      await onChanged?.();
      setNotice('Devolución registrada. El inventario se actualizó según el destino de cada artículo.');
    } catch (err) {
      setError(err?.message || 'No se pudo registrar la devolución.');
    } finally {
      setLoading(false);
    }
  }

  return <section className="returns-panel">
    <div className="panel-heading">
      <div><h3>Devoluciones de clientes</h3><p>Selecciona artículos devueltos y define cuáles vuelven al inventario.</p></div>
      <button type="button" className="button primary" onClick={openForm} disabled={formOpen}>+ Nueva devolución</button>
    </div>
    {error && !formOpen && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success" role="status" aria-live="polite"><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
    {!formOpen && <div className="table-wrap"><table><thead><tr><th>Devolución</th><th>Venta original</th><th>Estado</th><th>Fecha</th><th>Motivo</th><th>Total</th></tr></thead><tbody>
      {rows.map((row,index) => <tr key={idOf(row)||index}>
        <td><strong>{row.returnNumber || idOf(row)}</strong></td>
        <td>{row.saleNumber || row.sourceSaleId || '—'}</td>
        <td><span className={'pill ' + (row.status === 'POSTED' ? 'active' : 'inactive')}>{row.status || '—'}</span></td>
        <td>{date(row.createdAt)}</td><td>{row.reason || '—'}</td><td>{money(row.total)}</td>
      </tr>)}
    </tbody></table>{!rows.length && <div className="empty-state">{loading ? 'Cargando devoluciones…' : 'Todavía no hay devoluciones de clientes.'}</div>}</div>}

    {formOpen && <section className="return-workspace" aria-labelledby="sales-return-title">
      <div className="return-workspace-heading">
        <div><span className="eyebrow">DEVOLUCIÓN DE CLIENTE</span><h3 id="sales-return-title">Registrar devolución</h3><p>Selecciona la venta, indica las cantidades y revisa el destino de los artículos.</p></div>
        <button type="button" className="button secondary" disabled={loading} onClick={closeForm}><X size={16}/> Cancelar</button>
      </div>
      <StepProgress steps={['Venta original', 'Artículos y motivo', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
      {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}
      {error && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
      <form className="workflow-form" onSubmit={submit} noValidate>
        {step === 0 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><RotateCcw size={20}/></span><div><h2>¿Qué venta te están devolviendo?</h2><p>Solo se muestran ventas completadas.</p></div></div>
          <label className="field"><span>Venta original<b> *</b></span><select required value={sourceSaleId} disabled={loading} onChange={event => { setSourceSaleId(event.target.value); setValidationMessage(''); }}><option value="">Seleccionar venta</option>{sales.map(sale => <option key={idOf(sale)} value={idOf(sale)}>{sale.saleNumber || idOf(sale)} · {sale.customerName || 'Mostrador'} · {money(sale.total)}</option>)}</select></label>
          {!sales.length && <p className="form-hint">No hay ventas completadas disponibles para devolver.</p>}
          {sourceSaleId && loading && <p className="form-hint" role="status">Cargando artículos y cantidades pendientes…</p>}
          {sourceSale && <div className="workflow-review-grid"><div className="workflow-review-card"><small>Venta</small><strong>{sourceSale.saleNumber || idOf(sourceSale)}</strong></div><div className="workflow-review-card"><small>Cliente</small><strong>{sourceSale.customerName || 'Mostrador'}</strong></div><div className="workflow-review-card"><small>Artículos con devolución posible</small><strong>{lines.filter(line => line.remainingQuantity > 0).length}</strong></div></div>}
        </section>}

        {step === 1 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><RotateCcw size={20}/></span><div><h2>¿Qué artículos y cuántos?</h2><p>Indica la cantidad que volvió y si el producto puede venderse de nuevo.</p></div></div>
          {lines.length === 0 && <div className="empty-state">{loading ? 'Cargando artículos…' : 'No hay artículos para mostrar.'}</div>}
          <div className="workflow-line-list">{lines.map((line,index) => <section className="line-editor workflow-line" key={line.saleItemId || index}>
            <div className="line-editor-heading"><strong>{line.productName} {line.sku ? '· ' + line.sku : ''}</strong><small>Vendido: {line.originalQuantity} · Disponible para devolver: {line.remainingQuantity}</small></div>
            <div className="form-grid compact-grid">
              <label className="field"><span>Cantidad devuelta</span><input type="number" min="0" max={line.remainingQuantity} step="0.001" inputMode="decimal" value={line.quantity} disabled={loading || !line.remainingQuantity} onChange={event => updateLine(index,'quantity',event.target.value)} /></label>
              <label className="field"><span>Destino del artículo</span><select value={line.disposition} disabled={loading || !line.remainingQuantity} onChange={event => updateLine(index,'disposition',event.target.value)}><option value="RESTOCK">Volver al inventario</option><option value="SCRAP">Descartar o dañado</option></select></label>
            </div>
          </section>)}</div>
          <div className="workflow-inline-actions"><span className="workflow-inline-total"><small>Valor estimado</small><strong>{money(returnTotal)}</strong></span></div>
          <label className="field"><span>Motivo<b> *</b></span><textarea required maxLength="1000" rows="3" value={reason} onChange={event => { setReason(event.target.value); setValidationMessage(''); }} placeholder="Ej. Producto defectuoso o compra equivocada" /></label>
        </section>}

        {step === 2 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><RotateCcw size={20}/></span><div><h2>Confirma la devolución</h2><p>Verifica las cantidades y el destino de cada artículo antes de registrar.</p></div></div>
          <div className="workflow-review-grid"><div className="workflow-review-card"><small>Venta original</small><strong>{sourceSale?.saleNumber || idOf(sourceSale) || '—'}</strong></div><div className="workflow-review-card"><small>Motivo</small><strong>{reason.trim() || '—'}</strong></div><div className="workflow-review-card"><small>Artículos</small><strong>{lines.filter(line => Number(line.quantity) > 0).length}</strong></div><div className="workflow-review-card workflow-review-total"><small>Valor estimado</small><strong>{money(returnTotal)}</strong></div></div>
          <div className="workflow-review-lines">{lines.filter(line => Number(line.quantity) > 0).map(line => <div className="workflow-review-line" key={line.saleItemId}><div><strong>{line.productName}</strong><small>{line.quantity} unidades · {line.disposition === 'RESTOCK' ? 'Volver al inventario' : 'Descartar / dañado'}</small></div><strong>{money(Number(line.quantity) * line.unitPrice)}</strong></div>)}</div>
          <p className="form-hint">La API valida las cantidades que ya se hayan devuelto y actualiza solo los productos marcados para reincorporación.</p>
        </section>}

        <div className="form-actions workflow-actions">
          <button type="button" className="button secondary" disabled={loading} onClick={closeForm}>Cancelar</button>
          <div className="workflow-actions-right">
            {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={() => { setValidationMessage(''); setStep(value => Math.max(0, value - 1)); }}><ArrowLeft size={16}/> Atrás</button>}
            {step < 2 ? <button type="submit" className="button primary" disabled={loading || (step === 0 && !sales.length)}>{step === 0 ? 'Continuar' : 'Revisar devolución'} <ArrowRight size={16}/></button>
              : <button type="submit" className="button primary" disabled={loading || !sourceSale || !reason.trim() || !lines.some(line => Number(line.quantity) > 0)}>{loading ? 'Registrando…' : 'Confirmar devolución'}</button>}
          </div>
        </div>
      </form>
    </section>}
  </section>;
}

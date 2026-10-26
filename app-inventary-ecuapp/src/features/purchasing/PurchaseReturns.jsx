import { useCallback, useEffect, useState } from 'react';
import { api, listFrom } from '../../api';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';

function idOf(item) { return item?.id ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }

export default function PurchaseReturns({ onChanged }) {
  const [returns, setReturns] = useState([]);
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState(false);
  const [receiptId, setReceiptId] = useState('');
  const [receipt, setReceipt] = useState(null);
  const [lines, setLines] = useState([]);
  const [reason, setReason] = useState('');
  const dialogRef = useAccessibleDialog(modal, () => setModal(false));

  const refresh = useCallback(async () => {
    setLoading(true);
    const [returnResult, receiptResult] = await Promise.allSettled([
      api.purchaseReturns({ size: 100 }),
      api.goodsReceipts({ size: 100 })
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
    if (!receiptId) {
      setReceipt(null);
      setLines([]);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([api.goodsReceipt(receiptId), api.purchaseReturns({ goodsReceiptId: receiptId, size: 100 })])
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
    setError('');
    setNotice('');
    setModal(true);
  }

  function updateLine(index, quantity) {
    setLines(current => current.map((line,lineIndex) => lineIndex === index ? { ...line, quantity } : line));
  }

  async function submit(event) {
    event.preventDefault();
    const items = lines.filter(line => Number(line.quantity) > 0).map(line => ({ receiptLineId: line.receiptLineId, quantity: Number(line.quantity) }));
    if (!receiptId || !receipt || !reason.trim() || !items.length || items.some(item => !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      setError('Selecciona una recepción, indica el motivo y añade al menos una cantidad válida.');
      return;
    }
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await api.createPurchaseReturn({ goodsReceiptId: receiptId, reason: reason.trim(), items });
      setNotice('Devolución a proveedor contabilizada. El inventario se redujo mediante el backend.');
      setModal(false);
      setReceiptId('');
      setReceipt(null);
      setLines([]);
      await refresh();
      await onChanged?.();
    } catch (err) {
      setError(err?.message || 'No se pudo registrar la devolución al proveedor.');
    } finally {
      setLoading(false);
    }
  }

  const estimatedTotal = lines.reduce((sum,line) => sum + Math.max(0,Number(line.quantity || 0)) * line.unitCost, 0);

  return <section className="returns-panel">
    <div className="panel-heading">
      <div><h3>Devoluciones a proveedores</h3><p>Devuelve mercancía de recepciones contabilizadas, con control de cantidades ya devueltas.</p></div>
      <button className="button primary" onClick={openForm}>+ Nueva devolución</button>
    </div>
    {error && <div className="alert error"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success"><span>{notice}</span><button onClick={() => setNotice('')}>×</button></div>}
    <div className="table-wrap"><table><thead><tr><th>Devolución</th><th>Orden</th><th>Recepción</th><th>Estado</th><th>Fecha</th><th>Motivo</th><th>Total</th></tr></thead><tbody>
      {returns.map((row,index) => <tr key={idOf(row)||index}>
        <td><strong>{row.returnNumber || idOf(row)}</strong></td><td>{row.purchaseOrderNumber || row.purchaseOrderId || '—'}</td>
        <td>{row.receiptNumber || row.receiptId || '—'}</td><td><span className={'pill ' + (row.status === 'POSTED' ? 'active' : 'inactive')}>{row.status || '—'}</span></td>
        <td>{date(row.createdAt)}</td><td>{row.reason || '—'}</td><td>{money(row.total)}</td>
      </tr>)}
    </tbody></table>{!returns.length && <div className="empty-state">{loading ? 'Cargando devoluciones…' : 'Todavía no hay devoluciones a proveedores.'}</div>}</div>

    {modal && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setModal(false); }}>
      <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="purchase-return-modal-title" tabIndex={-1}>
        <div className="modal-heading"><h2 id="purchase-return-modal-title">Registrar devolución a proveedor</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={() => setModal(false)}>×</button></div>
        <form className="form-grid" onSubmit={submit}>
          <label className="field wide"><span>Recepción contabilizada<b> *</b></span><select required value={receiptId} onChange={event => setReceiptId(event.target.value)}><option value="">Seleccionar recepción</option>{receipts.map(item => <option key={idOf(item)} value={idOf(item)}>{item.receiptNumber || idOf(item)} · {item.purchaseOrderNumber || ''} · {item.warehouseCode || ''}</option>)}</select></label>
          {receipt && <p className="form-hint wide">Recepción: <strong>{receipt.receiptNumber || idOf(receipt)}</strong>. Solo se puede devolver la cantidad aceptada que aún no tenga devoluciones registradas.</p>}
          {lines.map((line,index) => <section className="line-editor wide" key={line.receiptLineId || index}>
            <div className="line-editor-heading"><strong>{line.sku || line.productId}</strong><small>Recibido: {line.receivedQuantity} · Disponible para devolver: {line.remainingQuantity}</small></div>
            <div className="form-grid compact-grid"><label className="field"><span>Cantidad a devolver</span><input type="number" min="0" max={line.remainingQuantity} step="0.001" value={line.quantity} disabled={!line.remainingQuantity} onChange={event => updateLine(index,event.target.value)} /></label><div className="stock-hint"><small>Costo de origen</small><strong>{money(line.unitCost)}</strong></div></div>
          </section>)}
          {receipt && <div className="wide order-total"><span>Valor estimado</span><strong>{money(estimatedTotal)}</strong></div>}
          <label className="field wide"><span>Motivo<b> *</b></span><textarea required maxLength="1000" rows="3" value={reason} onChange={event => setReason(event.target.value)} placeholder="Describe el motivo de la devolución" /></label>
          <p className="form-hint wide">La API valida que la devolución no supere lo aceptado en la recepción y descuenta el costo del inventario de origen.</p>
          <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => setModal(false)}>Cancelar</button><button className="button primary" disabled={loading || !receipt || !lines.some(line => Number(line.quantity) > 0) || !reason.trim()}>{loading ? 'Registrando…' : 'Registrar devolución'}</button></div>
        </form>
      </section>
    </div>}
  </section>;
}

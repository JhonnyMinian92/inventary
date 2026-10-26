import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, listFrom } from '../../api';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';

function idOf(value) { return value?.id ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }

export default function SalesReturns({ onChanged }) {
  const [rows, setRows] = useState([]);
  const [sales, setSales] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState(false);
  const [sourceSaleId, setSourceSaleId] = useState('');
  const [sourceSale, setSourceSale] = useState(null);
  const [lines, setLines] = useState([]);
  const [reason, setReason] = useState('');
  const dialogRef = useAccessibleDialog(modal, () => setModal(false));

  const refresh = useCallback(async () => {
    setLoading(true);
    const [returnsResult, salesResult] = await Promise.allSettled([
      api.salesReturns({ size: 100, sort: 'createdAt,desc' }),
      api.sales({ size: 100, sort: 'soldAt,desc' })
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
    if (!sourceSaleId) {
      setSourceSale(null);
      setLines([]);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    Promise.all([api.sale(sourceSaleId), api.salesReturns({ sourceSaleId, size: 100 })])
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
            disposition: 'RESTOCK'
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

  const returnTotal = useMemo(() => lines.reduce((sum, line) => {
    const saleLine = sourceSale?.items?.find(item => String(idOf(item)) === String(line.saleItemId));
    return sum + Math.max(0, Number(line.quantity || 0)) * Number(saleLine?.unitPrice || 0);
  }, 0), [lines, sourceSale]);

  function openForm() {
    setSourceSaleId('');
    setSourceSale(null);
    setLines([]);
    setReason('');
    setError('');
    setNotice('');
    setModal(true);
  }

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
  }

  async function submit(event) {
    event.preventDefault();
    const items = lines.filter(line => Number(line.quantity) > 0).map(line => ({
      saleItemId: line.saleItemId,
      quantity: Number(line.quantity),
      disposition: line.disposition
    }));
    if (!sourceSaleId || !sourceSale || !reason.trim() || !items.length ||
        items.some(item => !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      setError('Selecciona una venta, indica el motivo y añade al menos una cantidad válida.');
      return;
    }
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await api.createSaleReturn({ sourceSaleId, reason: reason.trim(), items });
      setNotice('Devolución registrada. Los artículos marcados para reincorporar vuelven al inventario mediante el backend.');
      setModal(false);
      setSourceSaleId('');
      setSourceSale(null);
      setLines([]);
      await refresh();
      await onChanged?.();
    } catch (err) {
      setError(err?.message || 'No se pudo registrar la devolución.');
    } finally {
      setLoading(false);
    }
  }

  return <section className="returns-panel">
    <div className="panel-heading">
      <div><h3>Devoluciones de clientes</h3><p>Registra productos devueltos y define si vuelven al inventario o se descartan.</p></div>
      <button className="button primary" onClick={openForm}>+ Nueva devolución</button>
    </div>
    {error && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success" role="status" aria-live="polite"><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
    <div className="table-wrap"><table><thead><tr><th>Devolución</th><th>Venta original</th><th>Estado</th><th>Fecha</th><th>Motivo</th><th>Total</th></tr></thead><tbody>
      {rows.map((row,index) => <tr key={idOf(row)||index}>
        <td><strong>{row.returnNumber || idOf(row)}</strong></td>
        <td>{row.saleNumber || row.sourceSaleId || '—'}</td>
        <td><span className={'pill ' + (row.status === 'POSTED' ? 'active' : 'inactive')}>{row.status || '—'}</span></td>
        <td>{date(row.createdAt)}</td><td>{row.reason || '—'}</td><td>{money(row.total)}</td>
      </tr>)}
    </tbody></table>{!rows.length && <div className="empty-state">{loading ? 'Cargando devoluciones…' : 'Todavía no hay devoluciones de clientes.'}</div>}</div>

    {modal && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setModal(false); }}>
      <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="sales-return-modal-title" tabIndex={-1}>
        <div className="modal-heading"><h2 id="sales-return-modal-title">Registrar devolución de cliente</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={() => setModal(false)}>×</button></div>
        <form className="form-grid" onSubmit={submit}>
          <label className="field wide"><span>Venta original<b> *</b></span><select required value={sourceSaleId} onChange={event => setSourceSaleId(event.target.value)}><option value="">Seleccionar una venta completada</option>{sales.map(sale => <option key={idOf(sale)} value={idOf(sale)}>{sale.saleNumber || idOf(sale)} · {sale.customerName || 'Mostrador'} · {money(sale.total)}</option>)}</select></label>
          {sourceSale && <p className="form-hint wide">Venta {sourceSale.saleNumber || idOf(sourceSale)} · {sourceSale.warehouseCode || 'Almacén'} · cada cantidad queda limitada a lo que aún no se ha devuelto.</p>}
          {lines.map((line,index) => <section className="line-editor wide" key={line.saleItemId || index}>
            <div className="line-editor-heading"><strong>{line.productName} {line.sku ? '· ' + line.sku : ''}</strong><small>Vendido: {line.originalQuantity} · Disponible para devolución: {line.remainingQuantity}</small></div>
            <div className="form-grid compact-grid">
              <label className="field"><span>Cantidad devuelta</span><input type="number" min="0" max={line.remainingQuantity} step="0.001" value={line.quantity} disabled={!line.remainingQuantity} onChange={event => updateLine(index,'quantity',event.target.value)} /></label>
              <label className="field"><span>Destino del artículo</span><select value={line.disposition} onChange={event => updateLine(index,'disposition',event.target.value)}><option value="RESTOCK">Volver al inventario</option><option value="SCRAP">Descartar / dañado</option></select></label>
            </div>
          </section>)}
          {sourceSale && <div className="wide order-total"><span>Valor estimado de la devolución</span><strong>{money(returnTotal)}</strong><small>El importe definitivo se calcula en el backend.</small></div>}
          <label className="field wide"><span>Motivo<b> *</b></span><textarea required maxLength="1000" rows="3" value={reason} onChange={event => setReason(event.target.value)} placeholder="Describe el motivo de la devolución" /></label>
          <p className="form-hint wide">El backend valida el límite de devoluciones anteriores y contabiliza únicamente los artículos marcados para reincorporación.</p>
          <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => setModal(false)}>Cancelar</button><button className="button primary" disabled={loading || !sourceSale || !lines.some(line => Number(line.quantity) > 0) || !reason.trim()}>{loading ? 'Registrando…' : 'Registrar devolución'}</button></div>
        </form>
      </section>
    </div>}
  </section>;
}

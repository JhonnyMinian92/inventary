import { useCallback, useEffect, useState } from 'react';
import { api, listFrom } from '../../api.js';
import { toast } from '../../ui/feedback.js';
import { Plus, RefreshCw } from 'lucide-react';

export default function RefundsPanel({ canManage = false }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState(null);
  const [providerReference, setProviderReference] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [returns, setReturns] = useState([]);
  const [returnsLoading, setReturnsLoading] = useState(false);
  const [form, setForm] = useState({ salesReturnId: '', amount: '', currency: 'USD', reason: '', paymentProvider: '', providerReference: '' });
  const load = useCallback(async () => {
    setLoading(true);
    try { setRows(listFrom(await api.refunds())); } catch (error) { toast.error(error?.message || 'No se pudieron cargar los reembolsos.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  async function openCreate() {
    setCreateOpen(true);
    setReturnsLoading(true);
    try {
      setReturns(listFrom(await api.salesReturns({ page: 0, size: 100, sort: 'createdAt,desc' })));
    } catch (error) {
      toast.error(error?.message || 'No se pudieron cargar las devoluciones.');
    } finally {
      setReturnsLoading(false);
    }
  }
  async function createRefund(event) {
    event.preventDefault();
    const salesReturn = returns.find(item => String(item.id) === String(form.salesReturnId));
    const amount = Number(form.amount);
    if (!salesReturn || !Number.isFinite(amount) || amount <= 0 || form.reason.trim().length < 3) {
      toast.error('Selecciona una devolución y completa el importe y el motivo.');
      return;
    }
    setActionLoading(true);
    try {
      await api.createRefund({
        saleId: salesReturn.sourceSaleId,
        salesReturnId: salesReturn.id,
        amount,
        currency: form.currency || 'USD',
        reason: form.reason.trim(),
        paymentProvider: form.paymentProvider.trim() || undefined,
        providerReference: form.providerReference.trim() || undefined
      });
      toast.success('Solicitud de reembolso creada.');
      setCreateOpen(false);
      setForm({ salesReturnId: '', amount: '', currency: 'USD', reason: '', paymentProvider: '', providerReference: '' });
      await load();
    } catch (error) {
      toast.error(error?.message || 'No se pudo crear el reembolso.');
    } finally {
      setActionLoading(false);
    }
  }
  const act = async (row, action, reference = '') => {
    if (!canManage) return;
    if (actionLoading) return;
    setActionLoading(true);
    try {
      await api.refundAction(row.id, action, reference);
      toast.success('Estado actualizado.');
      await load();
    } catch (error) { toast.error(error?.message || 'No se pudo actualizar el reembolso.'); }
    finally { setActionLoading(false); setPendingAction(null); setProviderReference(''); }
  };
  const requestAction = (row, action) => {
    if (action === 'complete') {
      setProviderReference(row.providerReference || '');
      setPendingAction({ row, action });
      return;
    }
    void act(row, action);
  };
  return <section className="subsection">
    <div className="table-card-header"><h3>Reembolsos</h3><div style={{display:'flex',gap:8}}>
      {canManage && <button type="button" className="button primary" disabled={loading || returnsLoading} onClick={openCreate}><Plus size={16}/> Nuevo reembolso</button>}
      <button type="button" className="button secondary" disabled={loading} onClick={load}><RefreshCw size={16}/> Actualizar</button>
    </div></div>
    {loading ? <p className="muted">Cargando reembolsos...</p> : rows.length === 0 ? <p className="muted">No hay reembolsos registrados.</p> : <div className="responsive-table">
      <table><thead><tr><th>Número</th><th>Estado</th><th>Monto</th><th>Motivo</th><th>Referencia</th><th></th></tr></thead>
      <tbody>{rows.map((row, index) => {
        const status = String(row.status || '').toUpperCase();
        return <tr key={row.id || row.refundNumber || index}>
        <td>{row.refundNumber}</td><td>{row.status}</td><td>{row.currency} {Number(row.amount).toFixed(2)}</td><td>{row.reason}</td><td>{row.providerReference || '-'}</td>
        <td>{canManage && <>
          {status === 'REQUESTED' && <button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'approve')}>Aprobar</button>}
          {status === 'APPROVED' && <><button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'processing')}>Procesar</button><button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'cancel')}>Cancelar</button></>}
          {status === 'PROCESSING' && <><button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'complete')}>Completar</button><button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'fail')}>Fallido</button><button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'cancel')}>Cancelar</button></>}
          {status === 'FAILED' && <button type="button" className="button secondary small" disabled={actionLoading} onClick={() => requestAction(row, 'cancel')}>Cancelar</button>}
        </>}</td>
      </tr>;
      })}</tbody></table>
    </div>}
    {pendingAction && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (!actionLoading && event.target === event.currentTarget) setPendingAction(null); }}>
      <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="refund-complete-title">
        <div className="modal-heading"><h2 id="refund-complete-title">Completar reembolso</h2></div>
        <label>Referencia del proveedor de pagos
          <input value={providerReference} onChange={event => setProviderReference(event.target.value)} maxLength={120} />
        </label>
        <div className="form-actions">
          <button type="button" className="button secondary" disabled={actionLoading} onClick={() => setPendingAction(null)}>Cerrar</button>
          <button type="button" className="button primary" disabled={actionLoading} onClick={() => act(pendingAction.row, pendingAction.action, providerReference)}>Completar</button>
        </div>
      </section>
    </div>}
    {createOpen && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (!actionLoading && event.target === event.currentTarget) setCreateOpen(false); }}>
      <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="refund-create-title">
        <div className="modal-heading"><h2 id="refund-create-title">Solicitar reembolso</h2></div>
        <form onSubmit={createRefund}>
          <label>Devolución de venta
            <select value={form.salesReturnId} onChange={event => {
              const selected = returns.find(item => String(item.id) === String(event.target.value));
              setForm(current => ({ ...current, salesReturnId: event.target.value, amount: selected?.total != null ? String(Number(selected.total).toFixed(2)) : current.amount }));
            }} disabled={returnsLoading || actionLoading} required>
              <option value="">Seleccionar devolución</option>
              {returns.map(item => <option key={item.id} value={item.id}>{item.returnNumber || item.id} · Venta {item.saleNumber || item.sourceSaleId}</option>)}
            </select>
          </label>
          <div className="form-grid two-columns">
            <label>Monto<input type="number" min="0.01" step="0.01" value={form.amount} onChange={event => setForm({ ...form, amount: event.target.value })} required /></label>
            <label>Moneda<input value={form.currency} maxLength={3} onChange={event => setForm({ ...form, currency: event.target.value.toUpperCase() })} required /></label>
          </div>
          <label>Motivo<textarea rows="3" minLength={3} maxLength={1000} value={form.reason} onChange={event => setForm({ ...form, reason: event.target.value })} required /></label>
          <label>Proveedor de pagos <small>(opcional)</small><input maxLength={80} value={form.paymentProvider} onChange={event => setForm({ ...form, paymentProvider: event.target.value })} /></label>
          <label>Referencia externa <small>(opcional)</small><input maxLength={160} value={form.providerReference} onChange={event => setForm({ ...form, providerReference: event.target.value })} /></label>
          {!returnsLoading && !returns.length && <p className="alert warning">Primero registra una devolución de venta.</p>}
          <div className="form-actions">
            <button type="button" className="button secondary" disabled={actionLoading} onClick={() => setCreateOpen(false)}>Cerrar</button>
            <button type="submit" className="button primary" disabled={actionLoading || returnsLoading || !returns.length}>{actionLoading ? 'Creando...' : 'Crear reembolso'}</button>
          </div>
        </form>
      </section>
    </div>}
  </section>;
}

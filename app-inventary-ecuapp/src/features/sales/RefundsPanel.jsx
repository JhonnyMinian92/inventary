import { useCallback, useEffect, useState } from 'react';
import { api, listFrom } from '../../api.js';
import { toast } from '../../ui/feedback.js';

export default function RefundsPanel({ canManage = false }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pendingAction, setPendingAction] = useState(null);
  const [providerReference, setProviderReference] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    try { setRows(listFrom(await api.refunds())); } catch (error) { toast.error(error?.message || 'No se pudieron cargar los reembolsos.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
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
    <div className="table-card-header"><h3>Reembolsos</h3><button type="button" className="button secondary" disabled={loading} onClick={load}>Actualizar</button></div>
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
  </section>;
}

import { useEffect, useState } from 'react';
import { api, listFrom } from '../../api.js';
import { toast } from '../../ui/feedback.js';

export default function RefundsPanel({ canManage = false }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const load = async () => {
    setLoading(true);
    try { setRows(listFrom(await api.refunds())); } catch (error) { toast.error(error?.message || 'No se pudieron cargar los reembolsos.'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const act = async (row, action) => {
    if (!canManage) return;
    try {
      const providerReference = action === 'complete' ? window.prompt('Referencia del proveedor de pagos (opcional):', row.providerReference || '') : '';
      await api.refundAction(row.id, action, providerReference);
      toast.success('Estado actualizado.');
      await load();
    } catch (error) { toast.error(error?.message || 'No se pudo actualizar el reembolso.'); }
  };
  return <section className="subsection">
    <div className="table-card-header"><h3>Reembolsos</h3><button type="button" className="button secondary" disabled={loading} onClick={load}>Actualizar</button></div>
    {loading ? <p className="muted">Cargando reembolsos...</p> : <div className="responsive-table">
      <table><thead><tr><th>Número</th><th>Estado</th><th>Monto</th><th>Motivo</th><th>Referencia</th><th></th></tr></thead>
      <tbody>{rows.map(row => <tr key={row.id}>
        <td>{row.refundNumber}</td><td>{row.status}</td><td>{row.currency} {Number(row.amount).toFixed(2)}</td><td>{row.reason}</td><td>{row.providerReference || '-'}</td>
        <td>{canManage && <>
          {row.status === 'REQUESTED' && <button type="button" className="button secondary small" onClick={() => act(row, 'approve')}>Aprobar</button>}
          {row.status === 'APPROVED' && <><button type="button" className="button secondary small" onClick={() => act(row, 'processing')}>Procesar</button><button type="button" className="button secondary small" onClick={() => act(row, 'cancel')}>Cancelar</button></>}
          {row.status === 'PROCESSING' && <><button type="button" className="button secondary small" onClick={() => act(row, 'complete')}>Completar</button><button type="button" className="button secondary small" onClick={() => act(row, 'fail')}>Fallido</button><button type="button" className="button secondary small" onClick={() => act(row, 'cancel')}>Cancelar</button></>}
          {row.status === 'FAILED' && <button type="button" className="button secondary small" onClick={() => act(row, 'cancel')}>Cancelar</button>}
        </>}</td>
      </tr>)}</tbody></table>
    </div>}
  </section>;
}

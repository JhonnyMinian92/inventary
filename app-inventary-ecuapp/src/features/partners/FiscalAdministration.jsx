import { useEffect, useState } from 'react';
import { request } from '../../shared/api/http/request.js';
import { toast } from '../../ui/feedback.js';

const emptyRate = { code: 'IVA_15', name: 'Tarifa IVA vigente', percentage: '15', validFrom: '', validUntil: '', active: true };

export default function FiscalAdministration({ canManage = false }) {
  const [rates, setRates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyRate);

  const load = async () => {
    setLoading(true);
    try { setRates(await request('/tax/rates')); } catch (error) { toast.error(error?.message || 'No se pudieron cargar las tarifas.'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const submit = async event => {
    event.preventDefault();
    try {
      const body = { ...form, percentage: Number(form.percentage), validUntil: form.validUntil || null };
      if (editing) await request('/tax/rates/' + editing.id, { method: 'PUT', body });
      else await request('/tax/rates', { method: 'POST', body });
      toast.success('Tarifa fiscal guardada.');
      setEditing(null); setForm(emptyRate); await load();
    } catch (error) { toast.error(error?.message || 'No se pudo guardar la tarifa.'); }
  };

  return <div className="management-grid">
    <div className="table-card">
      <div className="table-card-header"><h3>Tarifas vigentes</h3></div>
      {loading ? <p className="muted">Cargando...</p> : <div className="responsive-table">
        <table><thead><tr><th>Código</th><th>Nombre</th><th>Porcentaje</th><th>Desde</th><th>Hasta</th><th>Estado</th><th></th></tr></thead>
        <tbody>{rates.map(rate => <tr key={rate.id}>
          <td>{rate.code}</td><td>{rate.name}</td><td>{rate.percentage}%</td><td>{rate.validFrom}</td><td>{rate.validUntil || 'Indefinida'}</td><td>{rate.active ? 'Activa' : 'Inactiva'}</td>
          <td>{canManage && <button type="button" className="button secondary small" onClick={() => { setEditing(rate); setForm({ ...rate, percentage: String(rate.percentage), validUntil: rate.validUntil || '' }); }}>Editar</button>}</td>
        </tr>)}</tbody></table>
      </div>}
    </div>
    {canManage && <form className="panel form-card" onSubmit={submit}>
      <h3>{editing ? 'Editar tarifa fiscal' : 'Nueva vigencia fiscal'}</h3>
      <label>Código<input required maxLength={24} value={form.code} disabled={Boolean(editing)} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} /></label>
      <label>Nombre<input required maxLength={160} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
      <label>Porcentaje<input required type="number" min="0" max="100" step="0.0001" value={form.percentage} onChange={e => setForm({ ...form, percentage: e.target.value })} /></label>
      <label>Vigente desde<input required type="date" value={form.validFrom} onChange={e => setForm({ ...form, validFrom: e.target.value })} /></label>
      <label>Vigente hasta<input type="date" min={form.validFrom} value={form.validUntil} onChange={e => setForm({ ...form, validUntil: e.target.value })} /></label>
      <label><input type="checkbox" checked={form.active !== false} onChange={e => setForm({ ...form, active: e.target.checked })} /> Activa</label>
      <div className="form-actions"><button className="button primary" type="submit">Guardar</button>{editing && <button className="button secondary" type="button" onClick={() => { setEditing(null); setForm(emptyRate); }}>Cancelar</button>}</div>
    </form>}
  </div>;
}

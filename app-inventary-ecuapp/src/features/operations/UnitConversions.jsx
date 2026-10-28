import { useCallback, useEffect, useState } from 'react';
import { useMessageToast } from '../../ui/feedback.js';
import { api, listFrom } from '../../api';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';

function idOf(item) { return item?.id ?? ''; }

export default function UnitConversions({ units }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useMessageToast(error, setError);
  const [notice, setNotice] = useState('');
  useMessageToast(notice, setNotice, 'success');
  const [modal, setModal] = useState(false);
  const [form, setForm] = useState({ fromUnitId: '', toUnitId: '', factor: '1' });
  const dialogRef = useAccessibleDialog(modal, () => setModal(false));

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setRows(listFrom(await api.unitConversions()));
    } catch (err) {
      setError('No se pudieron cargar las conversiones de unidades: ' + (err?.message || 'error'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  function openForm() {
    const active = units.filter(unit => unit.active !== false && unit.status !== 'INACTIVE');
    setForm({ fromUnitId: active[0]?.id || '', toUnitId: active.find(unit => unit.id !== active[0]?.id)?.id || '', factor: '1' });
    setError('');
    setNotice('');
    setModal(true);
  }

  async function submit(event) {
    event.preventDefault();
    const factor = Number(form.factor);
    if (!form.fromUnitId || !form.toUnitId || form.fromUnitId === form.toUnitId) {
      setError('Selecciona dos unidades diferentes.');
      return;
    }
    if (!Number.isFinite(factor) || factor <= 0 || !/^\d{1,11}(\.\d{1,9})?$/.test(form.factor.trim())) {
      setError('El factor debe ser positivo y tener como máximo 11 enteros y 9 decimales.');
      return;
    }
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await api.createUnitConversion({ fromUnitId: form.fromUnitId, toUnitId: form.toUnitId, factor });
      setNotice('Conversión guardada.');
      setModal(false);
      await refresh();
    } catch (err) {
      setError(err?.message || 'No se pudo guardar la conversión.');
    } finally {
      setLoading(false);
    }
  }

  const activeUnits = units.filter(unit => unit.active !== false && unit.status !== 'INACTIVE');
  return <section className="unit-conversions">
    <div className="panel-heading">
      <div><h3>Conversiones entre unidades</h3><p>Define el factor para convertir una unidad de origen en una unidad de destino.</p></div>
      <button className="button secondary" onClick={openForm} disabled={activeUnits.length < 2}>+ Nueva conversión</button>
    </div>
    <div className="table-wrap"><table><thead><tr><th>Desde</th><th>Hacia</th><th>Factor</th></tr></thead><tbody>
      {rows.map((row,index) => <tr key={idOf(row)||index}><td>{row.fromUnitCode || row.fromUnitId || '—'}</td><td>{row.toUnitCode || row.toUnitId || '—'}</td><td><strong>{row.factor}</strong></td></tr>)}
    </tbody></table>{!rows.length && <div className="empty-state">{loading ? 'Cargando conversiones…' : 'No hay conversiones configuradas.'}</div>}</div>

    {modal && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setModal(false); }}>
      <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="unit-conversion-modal-title" tabIndex={-1}>
        <div className="modal-heading"><h2 id="unit-conversion-modal-title">Nueva conversión de unidades</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={() => setModal(false)}>×</button></div>
        <form className="form-grid" onSubmit={submit}>
          <label className="field"><span>Unidad de origen<b> *</b></span><select required value={form.fromUnitId} onChange={event => setForm(current => ({ ...current, fromUnitId: event.target.value }))}><option value="">Seleccionar unidad</option>{activeUnits.map(unit => <option key={idOf(unit)} value={idOf(unit)}>{unit.name} · {unit.code}</option>)}</select></label>
          <label className="field"><span>Unidad de destino<b> *</b></span><select required value={form.toUnitId} onChange={event => setForm(current => ({ ...current, toUnitId: event.target.value }))}><option value="">Seleccionar unidad</option>{activeUnits.map(unit => <option key={idOf(unit)} value={idOf(unit)}>{unit.name} · {unit.code}</option>)}</select></label>
          <label className="field wide"><span>Factor<b> *</b></span><input type="number" min="0.000000001" step="0.000000001" required value={form.factor} onChange={event => setForm(current => ({ ...current, factor: event.target.value }))} /><small className="field-help">Ejemplo: 1 caja equivale a 12 unidades → origen Caja, destino Unidad, factor 12.</small></label>
          <p className="form-hint wide">La API rechaza unidades iguales, inactivas o conversiones duplicadas.</p>
          <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => setModal(false)}>Cancelar</button><button className="button primary" disabled={loading || activeUnits.length < 2}>{loading ? 'Guardando…' : 'Guardar conversión'}</button></div>
        </form>
      </section>
    </div>}
  </section>;
}

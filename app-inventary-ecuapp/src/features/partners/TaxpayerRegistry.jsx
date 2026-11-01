import { useCallback, useEffect, useState } from 'react';
import { Plus, RefreshCw, Search, Eye, X, Save } from 'lucide-react';
import { api, listFrom } from '../../api.js';
import ServerPagination from '../../ui/ServerPagination.jsx';
import { useMessageToast } from '../../ui/feedback.js';
import './taxpayer-registry.css';

const emptyEstablishment = () => ({ code: '', tradeName: '', address: '', location: '', status: '', headOffice: false });
const emptyForm = () => ({ taxId: '', legalName: '', status: '', sourceReference: '', establishments: [emptyEstablishment()] });
const pageMeta = value => ({
  page: Number(value?.number ?? 0), size: Number(value?.size ?? 20),
  totalElements: Number(value?.totalElements ?? 0), totalPages: Number(value?.totalPages ?? 1)
});

export default function TaxpayerRegistry({ canManage = false }) {
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [pagination, setPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 1 });
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState({ establishments: [], snapshots: [] });
  const [detailLoading, setDetailLoading] = useState(false);
  useMessageToast(error, setError);
  useMessageToast(notice, setNotice, 'success');

  useEffect(() => {
    const timer = window.setTimeout(() => { setDebouncedQuery(query.trim()); setPage(0); }, 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api.taxpayers({ ...(debouncedQuery ? { q: debouncedQuery } : {}), page, size: 20, sort: 'legalName,asc' });
      setRows(listFrom(result));
      setPagination(pageMeta(result));
    } catch (err) {
      setRows([]);
      setPagination({ page: 0, size: 20, totalElements: 0, totalPages: 1 });
      setError(err?.status === 403 ? 'No tienes permiso para consultar los datos tributarios.' : (err?.message || 'No se pudo cargar el registro tributario.'));
    } finally { setLoading(false); }
  }, [debouncedQuery, page]);

  useEffect(() => { void refresh(); }, [refresh]);

  async function openDetail(item) {
    setSelected(item);
    setDetail({ establishments: [], snapshots: [] });
    setDetailLoading(true);
    try {
      const [establishments, snapshots] = await Promise.all([
        api.taxpayerEstablishments(item.id),
        api.taxpayerSnapshots(item.id, { page: 0, size: 10, sort: 'observedAt,desc' })
      ]);
      setDetail({ establishments: listFrom(establishments), snapshots: listFrom(snapshots) });
    } catch (err) {
      setError(err?.message || 'No se pudo cargar el historial tributario.');
    } finally { setDetailLoading(false); }
  }

  function updateEstablishment(index, field, value) {
    setForm(current => ({ ...current, establishments: current.establishments.map((row, i) => i === index ? { ...row, [field]: value } : row) }));
  }

  async function submitImport(event) {
    event.preventDefault();
    const taxId = form.taxId.trim();
    if (!/^\\d{13}$/.test(taxId)) { setError('El RUC debe contener exactamente 13 dígitos.'); return; }
    if (!form.legalName.trim() || !form.status.trim() || !form.sourceReference.trim()) { setError('Completa razón social, estado recibido y referencia de fuente.'); return; }
    if (!form.establishments.length || form.establishments.some(item => !/^\\d{3}$/.test(item.code) || !item.status.trim())) {
      setError('Cada establecimiento necesita un código de tres dígitos y su estado recibido.'); return;
    }
    setLoading(true);
    setError('');
    try {
      const payload = {
        taxId, legalName: form.legalName.trim(), status: form.status.trim(),
        sourceReference: form.sourceReference.trim(),
        establishments: form.establishments.map(item => ({
          code: item.code.trim(), tradeName: item.tradeName.trim() || null,
          address: item.address.trim() || null, location: item.location.trim() || null,
          status: item.status.trim(), headOffice: Boolean(item.headOffice)
        }))
      };
      const saved = await api.importTaxpayer(payload);
      setImportOpen(false);
      setForm(emptyForm());
      setNotice('Importación manual guardada. El registro permanece en revisión y no acredita verificación oficial del SRI.');
      await refresh();
      await openDetail(saved);
    } catch (err) {
      setError(err?.message || 'No se pudo importar el registro tributario.');
    } finally { setLoading(false); }
  }

  return <section className="taxpayer-registry">
    <header className="taxpayer-heading">
      <div><h2>Registro de contribuyentes</h2><p>Consulta snapshots y establecimientos. Las importaciones manuales siempre quedan en revisión.</p></div>
      <div className="taxpayer-actions">
        <button type="button" className="button secondary" onClick={() => void refresh()} disabled={loading}><RefreshCw size={16}/> Actualizar</button>
        {canManage && <button type="button" className="button primary" onClick={() => { setForm(emptyForm()); setImportOpen(true); }}><Plus size={16}/> Importar registro</button>}
      </div>
    </header>
    <div className="taxpayer-toolbar">
      <label className="taxpayer-search"><Search size={16}/><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar por RUC o razón social" aria-label="Buscar contribuyente"/></label>
      <span>{loading ? 'Actualizando…' : pagination.totalElements + ' contribuyentes'}</span>
    </div>
    <div className="table-wrap"><table>
      <thead><tr><th>RUC</th><th>Razón social</th><th>Estado recibido</th><th>Frescura</th><th>Observado</th><th>Detalle</th></tr></thead>
      <tbody>{rows.map(row => <tr key={row.id}>
        <td><strong>{row.taxId}</strong></td><td>{row.legalName}</td><td>{row.statusRaw || '—'}</td>
        <td><span className="taxpayer-review">{row.freshnessStatus === 'MANUAL_REVIEW' ? 'Revisión manual' : row.freshnessStatus || '—'}</span></td>
        <td>{row.observedAt ? new Date(row.observedAt).toLocaleString('es-EC') : '—'}</td>
        <td><button type="button" className="small-action" onClick={() => void openDetail(row)}><Eye size={14}/> Ver</button></td>
      </tr>)}</tbody>
    </table>{!rows.length && <div className="empty-state">{loading ? 'Cargando contribuyentes…' : error ? 'No se pudo cargar el registro.' : 'No hay contribuyentes que coincidan con la búsqueda.'}</div>}</div>
    <ServerPagination info={pagination} page={page} loading={loading} onChange={setPage}/>

    {selected && <div className="taxpayer-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setSelected(null); }}>
      <section className="taxpayer-drawer" role="dialog" aria-modal="true" aria-labelledby="taxpayer-detail-title">
        <header><div><h3 id="taxpayer-detail-title">{selected.legalName}</h3><p>{selected.taxId} · {selected.freshnessStatus === 'MANUAL_REVIEW' ? 'Revisión manual' : selected.freshnessStatus}</p></div><button type="button" className="icon-button" aria-label="Cerrar detalle" onClick={() => setSelected(null)}><X size={18}/></button></header>
        {detailLoading ? <p>Cargando historial…</p> : <>
          <h4>Establecimientos</h4>
          <div className="table-wrap"><table><thead><tr><th>Código</th><th>Nombre comercial</th><th>Estado</th><th>Dirección</th></tr></thead><tbody>
            {detail.establishments.map(item => <tr key={item.id}><td>{item.code}</td><td>{item.tradeName || '—'}</td><td>{item.statusRaw} ({item.statusNormalized})</td><td>{item.address || item.location || '—'}</td></tr>)}
          </tbody></table>{!detail.establishments.length && <p className="empty-state">Sin establecimientos registrados.</p>}</div>
          <h4>Snapshots de origen</h4>
          <div className="taxpayer-snapshots">{detail.snapshots.map(item => <article key={item.id}>
            <strong>{item.sourceType} · {item.sourceReference}</strong><small>{new Date(item.observedAt).toLocaleString('es-EC')} · {item.actor}</small><code>{item.payloadHash}</code>
          </article>)}</div>
        </>}
      </section>
    </div>}

    {importOpen && <div className="taxpayer-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setImportOpen(false); }}>
      <section className="taxpayer-drawer taxpayer-import" role="dialog" aria-modal="true" aria-labelledby="taxpayer-import-title">
        <header><div><h3 id="taxpayer-import-title">Importación manual del SRI</h3><p>Los datos se guardan con fuente, actor, fecha y hash. No se consulta automáticamente al SRI.</p></div><button type="button" className="icon-button" aria-label="Cerrar importación" onClick={() => setImportOpen(false)}><X size={18}/></button></header>
        <form onSubmit={submitImport}>
          <div className="taxpayer-form-grid">
            <label>RUC<input required inputMode="numeric" maxLength={13} pattern="[0-9]{13}" value={form.taxId} onChange={event => setForm(current => ({ ...current, taxId: event.target.value.replace(/\\D/g, '').slice(0, 13) }))}/></label>
            <label>Razón social<input required maxLength={180} value={form.legalName} onChange={event => setForm(current => ({ ...current, legalName: event.target.value }))}/></label>
            <label>Estado recibido<input required maxLength={100} value={form.status} onChange={event => setForm(current => ({ ...current, status: event.target.value }))}/></label>
            <label>Referencia de fuente<input required maxLength={160} placeholder="Documento / portal / fecha de consulta" value={form.sourceReference} onChange={event => setForm(current => ({ ...current, sourceReference: event.target.value }))}/></label>
          </div>
          <div className="taxpayer-establishments-heading"><h4>Establecimientos</h4><button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, establishments: [...current.establishments, emptyEstablishment()] }))}><Plus size={14}/> Añadir</button></div>
          {form.establishments.map((item, index) => <fieldset className="taxpayer-establishment" key={index}>
            <legend>Establecimiento {index + 1}</legend>
            <label>Código (3 dígitos)<input required maxLength={3} pattern="[0-9]{3}" inputMode="numeric" value={item.code} onChange={event => updateEstablishment(index,'code',event.target.value.replace(/\\D/g,'').slice(0,3))}/></label>
            <label>Nombre comercial<input maxLength={180} value={item.tradeName} onChange={event => updateEstablishment(index,'tradeName',event.target.value)}/></label>
            <label>Estado recibido<input required maxLength={80} value={item.status} onChange={event => updateEstablishment(index,'status',event.target.value)}/></label>
            <label>Dirección<input maxLength={500} value={item.address} onChange={event => updateEstablishment(index,'address',event.target.value)}/></label>
            <label>Localidad<input maxLength={180} value={item.location} onChange={event => updateEstablishment(index,'location',event.target.value)}/></label>
            <label className="taxpayer-checkbox"><input type="checkbox" checked={item.headOffice} onChange={event => updateEstablishment(index,'headOffice',event.target.checked)}/> Matriz</label>
            {form.establishments.length > 1 && <button type="button" className="small-action danger" onClick={() => setForm(current => ({ ...current, establishments: current.establishments.filter((_, i) => i !== index) }))}>Quitar</button>}
          </fieldset>)}
          <div className="taxpayer-warning">La importación parcial no elimina establecimientos anteriores. El estado queda en revisión manual y no habilita facturación.</div>
          <footer><button type="button" className="button secondary" disabled={loading} onClick={() => setImportOpen(false)}>Cancelar</button><button type="submit" className="button primary" disabled={loading}><Save size={16}/>{loading ? 'Guardando…' : 'Guardar importación'}</button></footer>
        </form>
      </section>
    </div>}
  </section>;
}

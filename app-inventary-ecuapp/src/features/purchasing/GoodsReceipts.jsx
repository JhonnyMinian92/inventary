import { useEffect, useState } from 'react';
import { Eye, PackageCheck, Search } from 'lucide-react';
import { api, listFrom } from '../../api';
import ServerPagination from '../../ui/ServerPagination.jsx';

const money = value => new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const date = value => value ? new Date(value).toLocaleString('es-EC') : '—';
const idOf = value => value?.id || value?.receiptId || '';

export default function GoodsReceipts() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [pagination, setPagination] = useState(null);

  async function load(nextPage = page, nextQuery = query) {
    setLoading(true);
    setError('');
    try {
      const result = await api.goodsReceipts({ page: nextPage, size: 20, sort: 'receivedAt,desc', q: nextQuery || undefined });
      setRows(listFrom(result));
      setPagination({ page: Number(result?.number ?? nextPage), size: Number(result?.size ?? 20), totalElements: Number(result?.totalElements ?? listFrom(result).length), totalPages: Number(result?.totalPages ?? 1) });
    } catch (requestError) {
      setError(requestError?.message || 'No se pudieron cargar las recepciones.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(0, query); }, []);

  return <section className="purchasing-workspace" aria-busy={loading}>
    <div className="purchasing-section-heading"><div><h3>Recepciones registradas</h3><p>Audita el ingreso de mercancía, costos registrados y documento de origen.</p></div></div>
    <form className="purchasing-filter-bar" onSubmit={event => { event.preventDefault(); setPage(0); void load(0, query); }}>
      <label className="search-field"><Search size={16}/><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar recepción o orden de compra" aria-label="Buscar recepciones"/></label>
      <button type="submit" className="button secondary" disabled={loading}>Buscar</button>
      {query && <button type="button" className="button secondary" onClick={() => { setQuery(''); setPage(0); void load(0, ''); }}>Limpiar</button>}
    </form>
    {error && <div className="alert error" role="alert">{error} <button type="button" className="text-button" onClick={() => void load(page, query)}>Reintentar</button></div>}
    <div className="table-wrap">
      <table aria-label="Recepciones de mercancía"><thead><tr><th>Recepción</th><th>Orden</th><th>Almacén</th><th>Fecha</th><th>Importe valorizado</th><th>Acciones</th></tr></thead>
        <tbody>{rows.map((row, index) => <tr key={idOf(row) || index}>
          <td><strong>{row.receiptNumber || idOf(row)}</strong></td><td>{row.purchaseOrderNumber || '—'}</td><td>{row.warehouseCode || '—'}</td><td>{date(row.receivedAt)}</td><td>{money(row.total)}</td>
          <td><button type="button" className="small-action" disabled={!idOf(row)} onClick={() => api.goodsReceipt(idOf(row)).then(detail => window.alert(`${detail.receiptNumber || 'Recepción'}\n${(detail.items || []).length} líneas registradas.`)).catch(requestError => setError(requestError?.message || 'No se pudo abrir la recepción.'))}><Eye size={13}/>Detalle</button></td>
        </tr>)}</tbody>
      </table>
      {loading ? <div className="empty-state">Cargando recepciones…</div> : !rows.length && !error ? <div className="empty-state">No hay recepciones que mostrar.</div> : null}
      <ServerPagination info={pagination} page={page} loading={loading} onChange={next => { setPage(next); void load(next, query); }} />
    </div>
  </section>;
}

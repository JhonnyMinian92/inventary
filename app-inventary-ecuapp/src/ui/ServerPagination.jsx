export default function ServerPagination({ page = 0, info, loading = false, onChange }) {
  const total = Number(info?.totalElements || 0);
  const size = Math.max(1, Number(info?.size || 20));
  const pages = Math.max(1, Number(info?.totalPages || Math.ceil(total / size) || 1));
  if (total <= size) return null;
  const start = page * size + 1;
  const end = Math.min(total, start + size - 1);
  return <nav className="pagination" aria-label="Paginación de resultados">
    <span aria-live="polite">{start}–{end} de {total.toLocaleString('es-EC')}</span>
    <div className="pagination-actions">
      <button type="button" className="icon-button" aria-label="Página anterior" disabled={loading || page <= 0}
        onClick={() => onChange?.(page - 1)}>‹</button>
      <span className="pagination-page" aria-current="page">Página {page + 1} de {pages}</span>
      <button type="button" className="icon-button" aria-label="Página siguiente" disabled={loading || page >= pages - 1}
        onClick={() => onChange?.(page + 1)}>›</button>
    </div>
  </nav>;
}

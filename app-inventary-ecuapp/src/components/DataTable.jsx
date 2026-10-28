import { useMemo } from 'react';

function rowKey(row, index) { return row?.id ?? row?.categoryId ?? [row?.productId, row?.warehouseId, row?.referenceId, index].filter(value => value !== null && value !== undefined && value !== '').join('-'); }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleString('es-EC');
}
function titleCase(value) {
  return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
}

export default function DataTable({ rows = [], columns = [], moneyKey, empty = 'No hay datos para mostrar.', caption = 'Resultados de la consulta' }) {
  const normalizedColumns = useMemo(() => columns.map(column => Array.isArray(column)
    ? { key: column[0], label: column[1] }
    : column), [columns]);
  return <div className="table-wrap">
    <table aria-label={caption}>
      <caption className="visually-hidden">{caption}</caption>
      <thead><tr>{normalizedColumns.map(column => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead>
      <tbody>
        {(rows || []).map((row, index) => <tr key={rowKey(row, index) || index}>
          {normalizedColumns.map(column => {
            const value = column.render ? column.render(row) :
              (column.key === moneyKey || column.format === 'money') ? money(row[column.key]) :
              (column.format === 'date' || ['createdAt','occurredAt','soldAt','orderedAt'].includes(column.key) || column.key.toLowerCase().includes('date')) ? date(row[column.key]) :
              ['status','movementType','type','referenceType'].includes(column.key) ? titleCase(row[column.key]) :
              row[column.key] ?? '—';
            return <td key={column.key}>{value === null || value === undefined || value === '' ? '—' : value}</td>;
          })}
        </tr>)}
      </tbody>
    </table>
    {!rows?.length && <div className="empty-state" role="status">{empty}</div>}
  </div>;
}

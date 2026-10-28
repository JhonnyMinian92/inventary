import { SERIES_COLOR } from './charts.js';

/** Barras horizontales de una sola serie con el valor en la punta. items: [{ id, label, sublabel, value }]. */
export default function BarList({ items = [], formatValue = String, empty = 'Sin datos para mostrar.', label, onSelect }) {
  if (!items.length) return <div className="chart-empty">{empty}</div>;
  const max = Math.max(...items.map(item => Number(item.value) || 0), 0) || 1;
  return <ul className="bar-list" aria-label={label}>
    {items.map(item => {
      const value = Number(item.value) || 0;
      const percent = value > 0 ? Math.max(1.5, value / max * 100) : 0;
      const content = <>
        <span className="bar-list-label"><strong>{item.label}</strong>{item.sublabel && <small>{item.sublabel}</small>}</span>
        <span className="bar-list-track" aria-hidden="true"><span style={{ width: percent + '%', background: SERIES_COLOR }} /></span>
        <span className="bar-list-value">{formatValue(value)}</span>
      </>;
      return <li key={item.id ?? item.label} title={`${item.label}: ${formatValue(value)}`}>
        {onSelect ? <button type="button" className="bar-list-row" onClick={() => onSelect(item)}>{content}</button> : <div className="bar-list-row">{content}</div>}
      </li>;
    })}
  </ul>;
}

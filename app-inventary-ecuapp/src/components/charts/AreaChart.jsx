import { useState } from 'react';
import { niceMax, SERIES_COLOR, useElementWidth } from './charts.js';

const PAD = { top: 18, right: 18, bottom: 30, left: 56 };

/**
 * Serie temporal única: área al 10 %, línea de 2 px, punto final etiquetado y cursor vertical con tooltip
 * (puntero y teclado). data: [{ key, label, value, detail }].
 */
export default function AreaChart({ data = [], height = 220, formatValue = String, formatAxis = formatValue, label }) {
  const [ref, width] = useElementWidth();
  const [active, setActive] = useState(null);
  const innerWidth = Math.max(0, width - PAD.left - PAD.right);
  const innerHeight = height - PAD.top - PAD.bottom;
  const max = niceMax(Math.max(0, ...data.map(point => Number(point.value) || 0)));
  const x = index => PAD.left + (data.length <= 1 ? innerWidth / 2 : index * innerWidth / (data.length - 1));
  const y = value => PAD.top + innerHeight - (Number(value) || 0) / max * innerHeight;
  const line = data.map((point, index) => `${index ? 'L' : 'M'}${x(index).toFixed(1)},${y(point.value).toFixed(1)}`).join(' ');
  const area = data.length ? `${line} L${x(data.length - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)} Z` : '';
  const ticks = [0, max / 2, max];
  const xLabels = data.length > 2 ? [0, Math.floor((data.length - 1) / 2), data.length - 1] : data.map((_, index) => index);
  const last = data.length - 1;
  const point = active === null ? null : data[active];

  function pick(clientX) {
    const box = ref.current?.getBoundingClientRect();
    if (!box || !data.length) return;
    const ratio = (clientX - box.left - PAD.left) / Math.max(1, innerWidth);
    setActive(Math.max(0, Math.min(last, Math.round(ratio * last))));
  }

  function onKeyDown(event) {
    if (!data.length) return;
    if (event.key === 'ArrowLeft') { event.preventDefault(); setActive(current => Math.max(0, (current ?? last) - 1)); }
    if (event.key === 'ArrowRight') { event.preventDefault(); setActive(current => Math.min(last, (current ?? last) + 1)); }
  }

  return <div ref={ref} className="chart-frame" style={{ height }}>
    {width > 0 && <svg width={width} height={height} role="img" aria-label={label} tabIndex={0}
      onPointerMove={event => pick(event.clientX)} onPointerLeave={() => setActive(null)} onFocus={() => setActive(last)} onBlur={() => setActive(null)} onKeyDown={onKeyDown}>
      {ticks.map(tick => <g key={tick}>
        <line x1={PAD.left} x2={width - PAD.right} y1={y(tick)} y2={y(tick)} className="chart-grid" />
        <text x={PAD.left - 10} y={y(tick)} className="chart-axis" textAnchor="end" dominantBaseline="middle">{formatAxis(tick)}</text>
      </g>)}
      {xLabels.map(index => <text key={index} x={x(index)} y={height - 8} className="chart-axis" textAnchor={index === 0 ? 'start' : index === last ? 'end' : 'middle'}>{data[index]?.label}</text>)}
      <path d={area} fill={SERIES_COLOR} fillOpacity=".1" />
      <path d={line} fill="none" stroke={SERIES_COLOR} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      {last >= 0 && active === null && <>
        <circle cx={x(last)} cy={y(data[last].value)} r="4" fill={SERIES_COLOR} stroke="#fff" strokeWidth="2" />
        <text x={x(last) - 8} y={y(data[last].value) - 10} className="chart-value" textAnchor="end">{formatValue(data[last].value)}</text>
      </>}
      {point && <>
        <line x1={x(active)} x2={x(active)} y1={PAD.top} y2={PAD.top + innerHeight} className="chart-crosshair" />
        <circle cx={x(active)} cy={y(point.value)} r="4" fill={SERIES_COLOR} stroke="#fff" strokeWidth="2" />
      </>}
    </svg>}
    {point && <div className="chart-tooltip" style={{ left: Math.min(Math.max(x(active), 90), width - 90), top: PAD.top }} role="status">
      <strong>{formatValue(point.value)}</strong>
      <span>{point.label}{point.detail ? ' · ' + point.detail : ''}</span>
    </div>}
  </div>;
}

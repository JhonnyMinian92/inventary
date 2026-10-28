import { SERIES_COLOR } from './charts.js';

/** Tendencia mínima para indicadores: línea atenuada y último punto en el color de serie. */
export default function Sparkline({ values = [], height = 34 }) {
  if (values.length < 2) return null;
  const width = 120;
  const max = Math.max(...values, 0) || 1;
  const step = width / (values.length - 1);
  const points = values.map((value, index) => [index * step, height - 3 - (Number(value) || 0) / max * (height - 6)]);
  const path = points.map(([px, py], index) => `${index ? 'L' : 'M'}${px.toFixed(1)},${py.toFixed(1)}`).join(' ');
  const [lastX, lastY] = points[points.length - 1];
  return <svg className="sparkline" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true" focusable="false">
    <path d={path} fill="none" stroke="#9fb8a8" strokeWidth="1.5" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
    <circle cx={lastX} cy={lastY} r="2.5" fill={SERIES_COLOR} />
  </svg>;
}

export function Skeleton({ width = '100%', height = 12, radius = 6, className = '' }) {
  return <span className={'skeleton ' + className} style={{ width, height, borderRadius: radius }} aria-hidden="true" />;
}

export function SkeletonTable({ rows = 6, columns = 5, label = 'Cargando registros' }) {
  return <div className="skeleton-table" role="status" aria-label={label}>
    <span className="visually-hidden">{label}…</span>
    {Array.from({ length: rows }, (_, row) => <div className="skeleton-table-row" key={row} style={{ gridTemplateColumns: `2fr repeat(${columns - 1}, 1fr)` }}>
      {Array.from({ length: columns }, (_, column) => <Skeleton key={column} height={column === 0 ? 14 : 11} width={column === 0 ? '80%' : '60%'} />)}
    </div>)}
  </div>;
}

export function SkeletonTiles({ count = 4 }) {
  return <div className="kpi-grid" role="status" aria-label="Cargando indicadores">
    {Array.from({ length: count }, (_, index) => <div className="kpi-tile" key={index}>
      <Skeleton width="45%" height={11} />
      <Skeleton width="65%" height={26} radius={8} />
      <Skeleton width="80%" height={10} />
    </div>)}
  </div>;
}

export function SkeletonBlock({ height = 220 }) {
  return <div className="skeleton-block" role="status" aria-label="Cargando gráfico"><Skeleton height={height} radius={10} /></div>;
}

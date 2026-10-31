export function BrandMark({ size = 64, className = '' }) {
  return <svg className={className} width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" focusable="false">
    <polygon points="32,3 57,17.5 57,46.5 32,61 7,46.5 7,17.5" fill="#1d3f2c" />
    <polygon points="32,3 57,17.5 32,32 7,17.5" fill="#2f5d42" />
    <polygon points="32,32 57,17.5 57,46.5 32,61" fill="#122a1d" />
    <g fill="none" stroke="#d6b45c" strokeWidth="2.2" strokeLinejoin="round">
      <polygon points="32,15 46,23 46,39 32,47 18,39 18,23" />
      <polyline points="18,23 32,31 46,23" />
      <line x1="32" y1="31" x2="32" y2="47" />
    </g>
  </svg>;
}

export default function BrandLogo({ className = '' }) {
  return <div className={'brand-logo ' + className}>
    <BrandMark className="brand-logo-mark" />
    <div className="brand-logo-copy"><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div>
  </div>;
}

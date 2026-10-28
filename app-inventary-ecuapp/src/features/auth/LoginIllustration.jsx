// Ilustración isométrica decorativa del login: almacén sobre una plataforma de cristal con paneles de métricas.
// Se dibuja con geometría simple para mantenerse nítida en cualquier resolución y sin peticiones adicionales.

const U = 15;
const COS = 0.866;
const SIN = 0.5;
const OX = 210;
const OY = 150;

function iso(x, y, z = 0) { return [OX + (x - y) * U * COS, OY + (x + y) * U * SIN - z * U]; }
function points(list) { return list.map(([a, b]) => `${a.toFixed(1)},${b.toFixed(1)}`).join(' '); }

const COLORS = {
  b: ['#eadcb2', '#cdb882', '#a99461'],
  g: ['#b6ecca', '#7fca9e', '#5aa47b'],
  s: ['#a3c9b1', '#729c83', '#577b66'],
  shelf: ['#3f6b53', '#2a4b3a', '#1f392c'],
  pallet: ['#8f7b52', '#6f5f3e', '#5a4d33'],
  robot: ['#2f7d55', '#1f5a3d', '#17452f'],
  slab: ['rgba(60,170,115,.16)', 'rgba(22,70,46,.88)', 'rgba(15,50,33,.92)'],
  glass: ['rgba(170,240,205,.08)', 'rgba(170,240,205,.06)', 'rgba(170,240,205,.1)']
};

const RISE = (e, f) => `matrix(${COS} ${-SIN} 0 1 ${e} ${f})`;
const FALL = (e, f) => `matrix(${COS} ${SIN} 0 1 ${e} ${f})`;
const FLAT = (e, f) => `matrix(${COS} ${SIN} ${-COS} ${SIN} ${e} ${f})`;
const FLOOR_UNITS = `matrix(${U * COS} ${U * SIN} ${-U * COS} ${U * SIN} ${OX} ${OY})`;

function Cuboid({ x, y, z = 0, w, d, h, colors, stroke = 'rgba(6,22,14,.45)', strokeWidth = 0.6 }) {
  const top = [iso(x, y, z + h), iso(x + w, y, z + h), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const left = [iso(x, y + d, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const right = [iso(x + w, y, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x + w, y, z + h)];
  return <g stroke={stroke} strokeWidth={strokeWidth} strokeLinejoin="round">
    <polygon points={points(left)} fill={colors[1]} />
    <polygon points={points(right)} fill={colors[2]} />
    <polygon points={points(top)} fill={colors[0]} />
  </g>;
}

function Post({ x, y, height }) {
  const [x1, y1] = iso(x, y, 0);
  const [x2, y2] = iso(x, y, height);
  return <line x1={x1} y1={y1} x2={x2} y2={y2} stroke="#86c3a1" strokeOpacity=".75" strokeWidth="1.4" strokeLinecap="round" />;
}

// rows: una cadena por nivel (de abajo arriba); b/g/s = caja beige/verde/salvia, mayúscula = caja alta, '.' = hueco.
function Rack({ x, y, w, d, rows, levelHeight = 1.25 }) {
  const height = rows.length * levelHeight + 0.12;
  const alongX = w >= d;
  const length = alongX ? w : d;
  return <g>
    <Post x={x} y={y} height={height} />
    <Post x={x + w} y={y} height={height} />
    <Post x={x} y={y + d} height={height} />
    {rows.map((row, level) => {
      const z = level * levelHeight;
      const slot = length / row.length;
      const size = slot * 0.8;
      return <g key={level}>
        <Cuboid x={x} y={y} z={z} w={w} d={d} h={0.12} colors={COLORS.shelf} />
        {[...row].map((code, index) => {
          if (code === '.') return null;
          const offset = index * slot + (slot - size) / 2;
          const boxHeight = code === code.toUpperCase() ? 0.98 : 0.74;
          const box = alongX
            ? { x: x + offset, y: y + (d - 0.8) / 2, w: size, d: 0.8 }
            : { x: x + (w - 0.8) / 2, y: y + offset, w: 0.8, d: size };
          return <Cuboid key={index} {...box} z={z + 0.12} h={boxHeight} colors={COLORS[code.toLowerCase()]} />;
        })}
      </g>;
    })}
    <Cuboid x={x} y={y} z={rows.length * levelHeight} w={w} d={d} h={0.12} colors={COLORS.shelf} />
    <Post x={x + w} y={y + d} height={height} />
  </g>;
}

function Panel({ transform, width, height, float, children }) {
  return <g className={float}>
    <g transform={transform}>
      <rect width={width} height={height} rx="7" fill="url(#li-panel)" stroke="rgba(120,230,170,.38)" />
      {children}
    </g>
  </g>;
}

function Connector({ from, to }) {
  return <line x1={from[0]} y1={from[1]} x2={to[0]} y2={to[1]} stroke="rgba(120,230,170,.32)" strokeWidth="1" strokeDasharray="3 4" />;
}

export default function LoginIllustration() {
  const platformTop = [iso(0, 0), iso(12, 0), iso(12, 12), iso(0, 12)];
  const gridLines = [2, 4, 6, 8, 10];
  return <svg className="auth-illustration" viewBox="0 0 420 470" preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false">
    <defs>
      <radialGradient id="li-halo" cx="50%" cy="50%" r="50%">
        <stop offset="0" stopColor="#40c882" stopOpacity=".32" />
        <stop offset="1" stopColor="#40c882" stopOpacity="0" />
      </radialGradient>
      <linearGradient id="li-panel" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#14341f" stopOpacity=".95" />
        <stop offset="1" stopColor="#0a1c13" stopOpacity=".92" />
      </linearGradient>
      <linearGradient id="li-area" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#5fe0a0" stopOpacity=".55" />
        <stop offset="1" stopColor="#5fe0a0" stopOpacity=".02" />
      </linearGradient>
      <linearGradient id="li-bar" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#8af3bb" />
        <stop offset="1" stopColor="#1f7a4f" />
      </linearGradient>
      <linearGradient id="li-arrow" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0" stopColor="#5fe0a0" stopOpacity=".08" />
        <stop offset="1" stopColor="#5fe0a0" stopOpacity=".8" />
      </linearGradient>
      <linearGradient id="li-arrow-y" x1="0" y1="1" x2="0" y2="0">
        <stop offset="0" stopColor="#5fe0a0" stopOpacity=".08" />
        <stop offset="1" stopColor="#5fe0a0" stopOpacity=".8" />
      </linearGradient>
      <filter id="li-glow" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="2.2" result="blur" />
        <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
      </filter>
      <filter id="li-soft" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="5" /></filter>
    </defs>

    <ellipse cx="210" cy="250" rx="200" ry="130" fill="url(#li-halo)" />

    <Panel transform={RISE(14, 118)} width={104} height={66} float="li-float-a">
      <circle cx="30" cy="33" r="17" fill="none" stroke="#25483a" strokeWidth="7" />
      <circle cx="30" cy="33" r="17" fill="none" stroke="#d6b45c" strokeWidth="7" strokeDasharray="66 107" transform="rotate(-90 30 33)" />
      <circle cx="30" cy="33" r="17" fill="none" stroke="#5fe0a0" strokeWidth="7" strokeDasharray="24 107" strokeDashoffset="-68" transform="rotate(-90 30 33)" />
      <rect x="58" y="20" width="34" height="4" rx="2" fill="#5fe0a0" opacity=".85" />
      <rect x="58" y="31" width="24" height="4" rx="2" fill="#5fe0a0" opacity=".45" />
      <rect x="58" y="42" width="30" height="4" rx="2" fill="#d6b45c" opacity=".8" />
    </Panel>

    <Panel transform={RISE(138, 84)} width={128} height={80} float="li-float-b">
      <rect x="10" y="8" width="34" height="3" rx="1.5" fill="rgba(255,255,255,.25)" />
      <circle className="li-pulse" cx="108" cy="10" r="2.6" fill="#6ef0a8" />
      <circle cx="117" cy="10" r="2.6" fill="#6ef0a8" opacity=".5" />
      {[30, 45, 60].map(y => <line key={y} x1="8" x2="120" y1={y} y2={y} stroke="rgba(255,255,255,.07)" />)}
      <path d="M8 72 L8 62 L22 50 L34 55 L48 36 L60 42 L74 24 L88 31 L100 16 L120 20 L120 72 Z" fill="url(#li-area)" />
      <path d="M8 62 L22 50 L34 55 L48 36 L60 42 L74 24 L88 31 L100 16 L120 20" fill="none" stroke="#63e6a2" strokeWidth="2" strokeLinejoin="round" filter="url(#li-glow)" />
    </Panel>

    <Panel transform={FALL(296, 28)} width={98} height={84} float="li-float-c">
      <rect x="10" y="8" width="30" height="3" rx="1.5" fill="rgba(255,255,255,.25)" />
      <circle className="li-pulse" cx="86" cy="10" r="2.6" fill="#6ef0a8" />
      {[22, 36, 28, 48, 40, 60, 54].map((height, index) => <rect key={index} x={10 + index * 12} y={76 - height} width="7" height={height} rx="1.5" fill="url(#li-bar)" />)}
    </Panel>

    <Connector from={[60, 160]} to={iso(0, 6)} />
    <Connector from={[338, 140]} to={iso(8, 0)} />

    <Panel transform={FLAT(96, 336)} width={112} height={66} float="li-float-c">
      <path d="M6 58 L6 40 L20 30 L30 36 L42 22 L54 30 L66 18 L78 28 L90 20 L106 32 L106 58 Z" fill="url(#li-area)" />
      <path d="M6 40 L20 30 L30 36 L42 22 L54 30 L66 18 L78 28 L90 20 L106 32" fill="none" stroke="#6ee7a8" strokeWidth="1.8" strokeLinejoin="round" />
    </Panel>

    <Panel transform={FLAT(330, 300)} width={96} height={62} float="li-float-a">
      {[[70, '#d6b45c'], [52, '#5fe0a0'], [78, '#d6b45c'], [40, '#5fe0a0']].map(([width, color], index) => <rect key={index} x="10" y={10 + index * 12} width={width} height="6" rx="3" fill={color} opacity=".85" />)}
    </Panel>

    <Panel transform={FLAT(214, 360)} width={92} height={72} float="li-float-b">
      <g fill="none" stroke="rgba(170,240,205,.55)" strokeWidth="1.2">
        <rect x="6" y="6" width="80" height="60" rx="2" />
        <path d="M6 32 H40 M40 6 V48 M60 32 H86 M60 48 V66" />
      </g>
      <rect x="12" y="12" width="9" height="9" fill="#cdb882" />
      <rect x="24" y="12" width="9" height="9" fill="#7fca9e" />
      <rect x="48" y="40" width="7" height="7" fill="#cdb882" />
      <rect x="68" y="12" width="11" height="9" fill="#7fca9e" />
      <rect x="14" y="44" width="11" height="11" fill="#cdb882" />
    </Panel>

    <Connector from={[214, 360]} to={iso(12, 12, -0.45)} />
    <Connector from={[140, 361]} to={iso(2, 12, -0.45)} />
    <Connector from={[330, 300]} to={iso(12, 3, -0.45)} />

    <Cuboid x={0} y={0} z={-0.45} w={12} d={12} h={0.45} colors={COLORS.slab} stroke="rgba(130,235,175,.55)" strokeWidth={1} />
    <polygon points={points(platformTop)} fill="none" stroke="#5fe0a0" strokeOpacity=".55" strokeWidth="3" filter="url(#li-soft)" />
    <g stroke="rgba(130,235,175,.09)" strokeWidth="1">
      {gridLines.map(n => <line key={'x' + n} x1={iso(n, 0)[0]} y1={iso(n, 0)[1]} x2={iso(n, 12)[0]} y2={iso(n, 12)[1]} />)}
      {gridLines.map(n => <line key={'y' + n} x1={iso(0, n)[0]} y1={iso(0, n)[1]} x2={iso(12, n)[0]} y2={iso(12, n)[1]} />)}
    </g>
    <g transform={FLOOR_UNITS}>
      <polygon points="2,6.65 5.3,6.65 5.3,6.25 6.4,7 5.3,7.75 5.3,7.35 2,7.35" fill="url(#li-arrow)" />
      <polygon points="9.25,6.6 9.25,4.1 8.85,4.1 9.6,3 10.35,4.1 9.95,4.1 9.95,6.6" fill="url(#li-arrow-y)" />
    </g>

    <Rack x={1} y={1.2} w={4} d={1} rows={['bBg.', 'gb.B', 'bgbg']} />
    <Rack x={1} y={4.4} w={4} d={1} rows={['B.gb', 'bbG.', '.gbs']} />
    <Rack x={7} y={1.2} w={1} d={4.2} rows={['gb.b', 'Bg.s', 'b.Gb']} />

    <Cuboid x={3.2} y={8.4} w={2} d={1.4} h={0.22} colors={COLORS.pallet} />
    <Cuboid x={3.3} y={8.55} z={0.22} w={0.85} d={1.1} h={0.75} colors={COLORS.b} />
    <Cuboid x={4.25} y={8.55} z={0.22} w={0.85} d={1.1} h={0.6} colors={COLORS.g} />
    <Cuboid x={3.3} y={8.55} z={0.97} w={0.85} d={1.1} h={0.55} colors={COLORS.b} />

    <Cuboid x={6.2} y={9} w={0.9} d={0.9} h={0.35} colors={COLORS.robot} />
    <Cuboid x={6.35} y={9.15} z={0.35} w={0.6} d={0.6} h={0.5} colors={COLORS.g} />

    <g>
      <Cuboid x={8.4} y={7.2} w={2.8} d={3.2} h={0.1} colors={COLORS.shelf} />
      <Cuboid x={8.7} y={7.5} z={0.1} w={0.9} d={0.9} h={0.8} colors={COLORS.b} />
      <Cuboid x={9.9} y={7.5} z={0.1} w={0.9} d={0.9} h={0.8} colors={COLORS.g} />
      <Cuboid x={8.7} y={9} z={0.1} w={0.9} d={0.9} h={0.8} colors={COLORS.b} />
      <Cuboid x={8.4} y={7.2} z={1.8} w={2.8} d={3.2} h={0.1} colors={COLORS.shelf} />
      <Cuboid x={9.2} y={8.1} z={1.9} w={1} d={1} h={0.9} colors={COLORS.b} />
      <Cuboid x={8.4} y={7.2} w={2.8} d={3.2} h={3.6} colors={COLORS.glass} stroke="rgba(170,240,205,.6)" strokeWidth={1} />
    </g>
  </svg>;
}

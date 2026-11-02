import { useEffect, useMemo, useState } from 'react';
import { API_BASE, getToken } from '../../api.js';
import { compactNumber, idOf, number } from '../../utils/format.js';

function amount(row, keys) {
  for (const key of keys) {
    const value = Number(row?.[key]);
    if (Number.isFinite(value)) return value;
  }
  return 0;
}

export default function IsometricOperationsScene({ products = [], warehouses = [], balances = [], customers = [], whatsappPending = 0, movements = [], onOpenProduct }) {
  const [hovered, setHovered] = useState(null);
  const [connected, setConnected] = useState(false);
  const productMap = useMemo(() => new Map(products.map(product => [String(idOf(product)), product])), [products]);

  useEffect(() => {
    if (!getToken()) return undefined;
    const controller = new AbortController();
    let stopped = false;

    const connect = async () => {
      try {
        const response = await fetch(`${API_BASE}/dashboard/events`, {
          headers: { Accept: 'text/event-stream', Authorization: `Bearer ${getToken()}` },
          signal: controller.signal
        });
        if (!response.ok || !response.body) throw new Error(`SSE ${response.status}`);
        setConnected(true);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (!stopped) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          if (buffer.includes('\n\n') || buffer.includes('\r\n\r\n')) {
            if (/event: snapshot/.test(buffer)) window.dispatchEvent(new Event('inventary:dashboard-realtime'));
            buffer = '';
          }
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setConnected(false);
      }
      if (!stopped && !controller.signal.aborted) window.setTimeout(connect, 5000);
    };

    void connect();
    return () => { stopped = true; controller.abort(); };
  }, []);

  const warehouseNodes = warehouses.slice(0, 4).map((warehouse, index) => {
    const warehouseId = String(idOf(warehouse));
    const rows = balances.filter(row => String(row.warehouseId || row.warehouseCode) === warehouseId || row.warehouseCode === warehouse.code);
    const stock = rows.reduce((sum, row) => sum + amount(row, ['onHand', 'availableQuantity', 'quantity']), 0);
    const topBalances = rows.filter(row => amount(row, ['onHand', 'availableQuantity', 'quantity']) > 0)
      .sort((a, b) => amount(b, ['onHand', 'availableQuantity', 'quantity']) - amount(a, ['onHand', 'availableQuantity', 'quantity']))
      .slice(0, 5);
    return { warehouse, warehouseId, stock, topBalances, x: 82 + (index % 2) * 330, y: 76 + Math.floor(index / 2) * 210 };
  });
  const deliveryCount = Math.min(Number(whatsappPending || 0), 4);
  const activeMovements = movements.filter(movement => movement.type && String(movement.type).toUpperCase().includes('OUT')).length;

  const show = (event, title, details, productId = null) => setHovered({
    x: event.clientX,
    y: event.clientY,
    title,
    details,
    productId
  });

  return <section className="iso-card" aria-label="Mapa operativo isométrico">
    <header className="iso-header">
      <div><span className="iso-eyebrow">Centro operativo</span><h2>Inventario en movimiento</h2></div>
      <span className={'realtime-status ' + (connected ? 'online' : '')}><i />{connected ? 'Actualización en vivo' : 'Reconectando'}</span>
    </header>
    <div className="iso-stage">
      <div className="iso-grid" />
      <div className="iso-personnel" onMouseEnter={event => show(event, 'Clientes', `${number(customers.length)} clientes registrados · ${activeMovements} movimientos de salida recientes`)}>
        <span className="iso-person">1</span><span className="iso-person">2</span><span className="iso-person">3</span>
        <small>{number(customers.length)} clientes</small>
      </div>
      <svg viewBox="0 0 760 430" role="img" aria-label="Mapa 2.5D de almacenes, productos, clientes y entregas">
        <defs>
          <linearGradient id="isoFloor" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#edf7f0"/><stop offset="1" stopColor="#dcebe1"/></linearGradient>
          <linearGradient id="isoBuilding" x1="0" y1="0" x2="1" y2="1"><stop stopColor="#55ad78"/><stop offset="1" stopColor="#26704a"/></linearGradient>
          <filter id="isoShadow"><feGaussianBlur stdDeviation="7"/></filter>
        </defs>
        <ellipse cx="380" cy="370" rx="330" ry="44" fill="#9cb1a3" opacity=".25" filter="url(#isoShadow)"/>
        <path d="M70 250 L380 100 L690 250 L380 400 Z" fill="url(#isoFloor)" stroke="#c8dacf"/>
        {warehouseNodes.map((node, warehouseIndex) => {
          const palletY = node.y + 78;
          return <g key={node.warehouseId || warehouseIndex} className="iso-building"
            onMouseEnter={event => show(event, node.warehouse.name || node.warehouse.code || 'Almacén', `${compactNumber(node.stock)} unidades disponibles`)}
            onMouseLeave={() => setHovered(null)}>
            <path d={`M${node.x} ${node.y + 60} l110 -55 110 55 -110 58z`} fill="#2f8558"/>
            <path d={`M${node.x} ${node.y + 60} l110 58v78l-110 -58z`} fill="#246644"/>
            <path d={`M${node.x + 220} ${node.y + 60} l-110 58v78l110 -58z`} fill="#397f58"/>
            <path d={`M${node.x + 110} ${node.y + 5} l110 55 -110 58 -110 -55z`} fill="url(#isoBuilding)"/>
            <text x={node.x + 110} y={palletY - 14} textAnchor="middle" className="iso-building-label">{node.warehouse.name || node.warehouse.code || 'Almacén'}</text>
            {node.topBalances.map((row, productIndex) => {
              const product = productMap.get(String(row.productId));
              const quantity = amount(row, ['onHand', 'availableQuantity', 'quantity']);
              const x = node.x + 45 + (productIndex % 5) * 27;
              const y = palletY + Math.floor(productIndex / 5) * 18;
              return <rect key={row.productId || productIndex} x={x} y={y} width="20" height={12 + Math.min(25, quantity / Math.max(1, node.stock) * 55)}
                rx="3" fill={['#efb74c', '#dc7658', '#72aee8', '#aa7bc8', '#53aaa0'][productIndex % 5]}
                className="iso-product" onClick={() => product && onOpenProduct?.(idOf(product))}
                onMouseEnter={event => show(event, product?.name || row.productName || row.sku || 'Producto',
                  `${compactNumber(quantity)} disponibles · SKU ${product?.sku || row.sku || '—'} · ${node.warehouse.name || node.warehouse.code || 'Almacén'}`,
                  idOf(product))}
                onMouseLeave={() => setHovered(null)}/>
            })}
          </g>;
        })}
        {Array.from({ length: deliveryCount }, (_, index) => {
          const x = 650 - index * 18;
          const y = 300 - index * 12;
          return <g key={index} className="iso-truck" onMouseEnter={event => show(event, 'Entrega WhatsApp', `${deliveryCount} pedidos pendientes de cobro o preparación · actividad de entrega registrada en pedidos`)}>
            <path d={`M${x} ${y} l31 -16 31 16 -31 16z`} fill="#e8f2ec"/>
            <path d={`M${x} ${y} l31 16v18l-31 -16z`} fill="#357f57"/>
            <path d={`M${x + 31} ${y + 16} l31 -16v18l-31 16z`} fill="#255f43"/>
            <circle cx={x + 20} cy={y + 30} r="5" fill="#183428"/><circle cx={x + 51} cy={y + 14} r="5" fill="#183428"/>
          </g>;
        })}
        {warehouseNodes.map((node, index) => <path key={`route-${index}`} d={`M${node.x + 110} ${node.y + 190} C380 330 520 330 650 300`} className="iso-route" />)}
      </svg>
      {hovered && <div className="iso-tooltip" style={{ left: hovered.x, top: hovered.y }}>
        <strong>{hovered.title}</strong><span>{hovered.details}</span>
        {hovered.productId && <button type="button" onClick={() => onOpenProduct?.(hovered.productId)}>Ver producto</button>}
      </div>}
    </div>
  </section>;
}

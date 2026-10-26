import { useMemo, useState } from 'react';

const emptyLine = () => ({ productId: '', quantity: '1' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }

export default function SaleForm({ products, warehouses, customers, balances, loading, onCancel, onSubmit }) {
  const [warehouseId, setWarehouseId] = useState(warehouses[0]?.id || '');
  const [customerId, setCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [lines, setLines] = useState([emptyLine()]);
  const totals = useMemo(() => lines.reduce((result, line) => {
    const product = products.find(item => String(idOf(item)) === String(line.productId));
    const qty = Math.max(0, Number(line.quantity || 0));
    result.quantity += qty;
    result.amount += qty * Math.max(0, Number(product?.salePrice || 0));
    return result;
  }, { quantity: 0, amount: 0 }), [lines, products]);

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
  }

  function available(productId) {
    return balances.filter(balance => String(balance.productId || balance.product?.id) === String(productId) &&
      String(balance.warehouseId || balance.warehouse?.id) === String(warehouseId))
      .reduce((sum, balance) => sum + Number(balance.available ?? balance.onHand ?? 0), 0);
  }

  function submit(event) {
    event.preventDefault();
    const items = lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity) }));
    if (!warehouseId || !items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0)) return;
    const data = { warehouseId, channel: 'POS', items };
    if (customerId) data.customerId = customerId;
    else if (customerName.trim()) data.customerName = customerName.trim();
    onSubmit(data);
  }

  return <form className="form-grid" onSubmit={submit}>
    <label className="field">
      <span>Almacén de salida<b> *</b></span>
      <select required value={warehouseId} onChange={event => setWarehouseId(event.target.value)}>
        <option value="">Seleccionar almacén</option>
        {warehouses.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.code || idOf(item)}</option>)}
      </select>
    </label>
    <label className="field">
      <span>Cliente registrado</span>
      <select value={customerId} onChange={event => { setCustomerId(event.target.value); if (event.target.value) setCustomerName(''); }}>
        <option value="">Venta rápida / otro cliente</option>
        {customers.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.code || idOf(item)}</option>)}
      </select>
    </label>
    {!customerId && <label className="field wide"><span>Nombre del cliente (opcional)</span><input maxLength="160" value={customerName} onChange={event => setCustomerName(event.target.value)} placeholder="Cliente de mostrador" /></label>}

    <div className="form-subheading wide"><strong>Artículos de la venta</strong><small>El precio final y la disponibilidad se calculan y validan en la transacción del backend.</small></div>
    {lines.map((line, index) => {
      const product = products.find(item => String(idOf(item)) === String(line.productId));
      const stock = line.productId && warehouseId ? available(line.productId) : null;
      const qty = Number(line.quantity || 0);
      return <section className="line-editor wide" key={index}>
        <div className="line-editor-heading"><strong>Artículo {index + 1}</strong>{lines.length > 1 && <button type="button" className="text-button danger-text" onClick={() => setLines(current => current.filter((_, lineIndex) => lineIndex !== index))}>Quitar</button>}</div>
        <div className="form-grid compact-grid">
          <label className="field wide"><span>Producto<b> *</b></span><select required value={line.productId} onChange={event => updateLine(index, 'productId', event.target.value)}><option value="">Seleccionar producto</option>{products.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label>
          <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" required value={line.quantity} onChange={event => updateLine(index, 'quantity', event.target.value)} /></label>
          <div className="stock-hint"><small>Stock disponible</small><strong>{stock == null ? '—' : stock.toLocaleString('es-EC')}</strong><small>Precio actual: {product ? money(product.salePrice) : '—'}</small></div>
        </div>
        {stock != null && qty > stock && <p className="inline-warning">La cantidad supera el stock mostrado. El backend rechazará la venta si no hay disponibilidad suficiente.</p>}
      </section>;
    })}
    <div className="wide line-add-row"><button type="button" className="button secondary" onClick={() => setLines(current => [...current, emptyLine()])}>+ Añadir artículo</button><div className="order-total"><span>Total estimado</span><strong>{money(totals.amount)}</strong><small>Estimación con precios de catálogo · {totals.quantity} unidades</small></div></div>
    <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading || !lines.length}>{loading ? 'Registrando…' : 'Registrar venta'}</button></div>
  </form>;
}

import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api';

const emptyLine = () => ({ productId: '', quantity: '1' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }

export default function SaleForm({ products, warehouses, customers, balances, loading, onCancel, onSubmit, onError }) {
  const [warehouseId, setWarehouseId] = useState(warehouses[0]?.id || '');
  const [customerId, setCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [lines, setLines] = useState([emptyLine()]);
  const [availabilityByProduct, setAvailabilityByProduct] = useState({});
  const [availabilityWarning, setAvailabilityWarning] = useState('');
  const selectedProductIds = useMemo(() => [...new Set(lines.map(line => line.productId).filter(Boolean))].sort().join(','), [lines]);
  const totals = useMemo(() => lines.reduce((result, line) => {
    const product = products.find(item => String(idOf(item)) === String(line.productId));
    const qty = Math.max(0, Number(line.quantity || 0));
    result.quantity += qty;
    result.amount += qty * Math.max(0, Number(product?.salePrice || 0));
    return result;
  }, { quantity: 0, amount: 0 }), [lines, products]);

  useEffect(() => {
    if (!warehouseId || !selectedProductIds) {
      setAvailabilityByProduct({});
      setAvailabilityWarning('');
      return undefined;
    }
    let active = true;
    const ids = selectedProductIds.split(',').filter(Boolean);
    Promise.all(ids.map(async productId => {
      try {
        const result = await api.availableStock({ productId, warehouseId });
        return [productId, Number(result?.available ?? 0)];
      } catch {
        return [productId, null];
      }
    })).then(entries => {
      if (!active) return;
      const next = Object.fromEntries(entries.filter(([,value]) => value !== null));
      setAvailabilityByProduct(next);
      setAvailabilityWarning(entries.some(([,value]) => value === null) ? 'No fue posible consultar la disponibilidad en tiempo real de algunos artículos; las existencias mostradas son aproximadas.' : '');
    });
    return () => { active = false; };
  }, [warehouseId, selectedProductIds]);

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
  }

  function available(productId) {
    if (Object.prototype.hasOwnProperty.call(availabilityByProduct, productId)) return availabilityByProduct[productId];
    return balances.filter(balance => String(balance.productId || balance.product?.id) === String(productId) &&
      String(balance.warehouseId || balance.warehouse?.id) === String(warehouseId))
      .reduce((sum, balance) => sum + Number(balance.available ?? balance.onHand ?? 0), 0);
  }

  function submit(event) {
    event.preventDefault();
    const items = lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity) }));
    if (!warehouseId) {
      onError?.('Selecciona el almacén desde el que saldrá la mercancía.');
      return;
    }
    if (!items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      onError?.('Selecciona un producto y confirma que cada cantidad sea mayor que cero.');
      return;
    }
    const productIds = items.map(item => String(item.productId));
    if (new Set(productIds).size !== productIds.length) {
      onError?.('Cada producto debe aparecer una sola vez en la venta.');
      return;
    }
    const data = { warehouseId, channel: 'POS', items };
    if (customerId) data.customerId = customerId;
    else {
      if (customerName.trim()) data.customerName = customerName.trim();
      if (customerPhone.trim()) data.customerPhone = customerPhone.trim();
    }
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
      <select value={customerId} onChange={event => { setCustomerId(event.target.value); if (event.target.value) { setCustomerName(''); setCustomerPhone(''); } }}>
        <option value="">Venta rápida / otro cliente</option>
        {customers.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.code || idOf(item)}</option>)}
      </select>
    </label>
    {!customerId && <><label className="field"><span>Nombre del cliente (opcional)</span><input maxLength="160" value={customerName} onChange={event => setCustomerName(event.target.value)} placeholder="Cliente de mostrador" /></label><label className="field"><span>Teléfono del cliente (opcional)</span><input maxLength="40" value={customerPhone} onChange={event => setCustomerPhone(event.target.value)} placeholder="099…" /></label></>}

    <div className="form-subheading wide"><strong>Artículos de la venta</strong><small>El precio y el stock se confirman al registrar la venta.</small></div>
    {lines.map((line, index) => {
      const product = products.find(item => String(idOf(item)) === String(line.productId));
      const stock = line.productId && warehouseId ? available(line.productId) : null;
      const qty = Number(line.quantity || 0);
      return <section className="line-editor wide" key={index}>
        <div className="line-editor-heading"><strong>Artículo {index + 1}</strong>{lines.length > 1 && <button type="button" className="text-button danger-text" onClick={() => setLines(current => current.filter((_, lineIndex) => lineIndex !== index))}>Quitar</button>}</div>
        <div className="form-grid compact-grid">
          <label className="field wide"><span>Producto<b> *</b></span><select required value={line.productId} onChange={event => updateLine(index, 'productId', event.target.value)}><option value="">Seleccionar producto</option>{products.filter(item => item.active !== false && item.status !== 'INACTIVE' && (item.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD' && !lines.some((otherLine, otherIndex) => otherIndex !== index && String(otherLine.productId) === String(idOf(item)))).map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label>
          <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" required value={line.quantity} onChange={event => updateLine(index, 'quantity', event.target.value)} /></label>
          <div className="stock-hint"><small>Stock disponible</small><strong>{stock == null ? '—' : stock.toLocaleString('es-EC')}</strong><small>Precio actual: {product ? money(product.salePrice) : '—'}</small></div>
        </div>
        {stock != null && qty > stock && <p className="inline-warning">La cantidad supera el stock mostrado. El backend rechazará la venta si no hay disponibilidad suficiente.</p>}
      </section>;
    })}
    {availabilityWarning && <p className="form-hint wide">{availabilityWarning}</p>}
    <div className="wide line-add-row"><button type="button" className="button secondary" disabled={lines.length >= 100} onClick={() => setLines(current => [...current, emptyLine()])}>+ Añadir artículo</button><div className="order-total"><span>Total estimado</span><strong>{money(totals.amount)}</strong><small>Estimación con precios de catálogo · {totals.quantity} unidades</small></div></div>
    <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading || !lines.length}>{loading ? 'Registrando…' : 'Registrar venta'}</button></div>
  </form>;
}

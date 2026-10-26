import { useMemo, useState } from 'react';

const emptyLine = () => ({ productId: '', quantity: '1', unitCost: '0' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }

export default function PurchaseForm({ products, warehouses, suppliers, loading, initialValue, onCancel, onSubmit }) {
  const [warehouseId, setWarehouseId] = useState(initialValue?.warehouseId || warehouses[0]?.id || '');
  const [supplierId, setSupplierId] = useState(initialValue?.supplierId || initialValue?.supplier?.id || '');
  const [notes, setNotes] = useState(initialValue?.notes || '');
  const [lines, setLines] = useState(() => {
    if (Array.isArray(initialValue?.items) && initialValue.items.length) {
      return initialValue.items.map(line => ({ productId: line.productId || line.product?.id || '', quantity: String(line.quantity ?? 1), unitCost: String(line.unitCost ?? line.costPrice ?? 0) }));
    }
    return [emptyLine()];
  });
  const total = useMemo(() => lines.reduce((sum, line) => sum + Math.max(0, Number(line.quantity || 0)) * Math.max(0, Number(line.unitCost || 0)), 0), [lines]);

  function updateLine(index, field, value) {
    setLines(current => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line));
  }

  function submit(event) {
    event.preventDefault();
    const items = lines.map(line => ({ productId: line.productId, quantity: Number(line.quantity), unitCost: Number(line.unitCost) }));
    if (!warehouseId || !supplierId || !items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0 || !Number.isFinite(item.unitCost) || item.unitCost < 0)) return;
    onSubmit({ warehouseId, supplierId, notes: notes.trim(), items });
  }

  return <form className="form-grid" onSubmit={submit}>
    <label className="field">
      <span>Almacén de destino<b> *</b></span>
      <select required value={warehouseId} onChange={event => setWarehouseId(event.target.value)}>
        <option value="">Seleccionar almacén</option>
        {warehouses.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.code || idOf(item)}</option>)}
      </select>
    </label>
    <label className="field">
      <span>Proveedor<b> *</b></span>
      <select required value={supplierId} onChange={event => setSupplierId(event.target.value)}>
        <option value="">Seleccionar proveedor</option>
        {suppliers.filter(item => item.active !== false && item.status !== 'INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.code || idOf(item)}</option>)}
      </select>
    </label>

    <label className="field wide"><span>Notas de la orden</span><textarea rows="2" maxLength="1000" value={notes} onChange={event => setNotes(event.target.value)} placeholder="Referencia, entrega o condición de compra" /></label>
    <div className="form-subheading wide"><strong>Productos de la orden</strong><small>Añade todas las líneas necesarias; cada cantidad y costo se validan antes de enviarse al backend.</small></div>
    {lines.map((line, index) => {
      const product = products.find(item => String(idOf(item)) === String(line.productId));
      const lineTotal = Math.max(0, Number(line.quantity || 0)) * Math.max(0, Number(line.unitCost || 0));
      return <section className="line-editor wide" key={index}>
        <div className="line-editor-heading"><strong>Línea {index + 1}</strong>{lines.length > 1 && <button type="button" className="text-button danger-text" onClick={() => setLines(current => current.filter((_, lineIndex) => lineIndex !== index))}>Quitar</button>}</div>
        <div className="form-grid compact-grid">
          <label className="field wide">
            <span>Producto<b> *</b></span>
            <select required value={line.productId} onChange={event => updateLine(index, 'productId', event.target.value)}>
              <option value="">Seleccionar producto</option>
              {products.filter(item => item.active !== false && item.status !== 'INACTIVE' && (item.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD' && !lines.some((otherLine, otherIndex) => otherIndex !== index && String(otherLine.productId) === String(idOf(item)))).map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}
            </select>
          </label>
          <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" required value={line.quantity} onChange={event => updateLine(index, 'quantity', event.target.value)} /></label>
          <label className="field"><span>Costo unitario<b> *</b></span><input type="number" min="0" step="0.01" required value={line.unitCost} onChange={event => updateLine(index, 'unitCost', event.target.value)} /></label>
        </div>
        <div className="line-editor-foot"><span>{product ? 'SKU: ' + (product.sku || product.code || '—') : 'Selecciona el producto'}</span><strong>{money(lineTotal)}</strong></div>
      </section>;
    })}
    <div className="wide line-add-row"><button type="button" className="button secondary" disabled={lines.length >= 100} onClick={() => setLines(current => [...current, emptyLine()])}>+ Añadir producto</button><div className="order-total"><span>Total estimado</span><strong>{money(total)}</strong></div></div>
    <p className="form-hint wide">La orden se crea como borrador. Después usa <strong>Enviar</strong> en la lista de compras; un usuario autorizado distinto debe aprobarla y luego puedes contabilizar la recepción. Solo esa recepción aumenta las existencias.</p>
    <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading || !lines.length}>{loading ? 'Guardando…' : 'Crear orden'}</button></div>
  </form>;
}

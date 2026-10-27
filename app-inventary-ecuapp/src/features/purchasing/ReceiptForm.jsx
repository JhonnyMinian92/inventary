import { useMemo, useState } from 'react';

function idOf(item) { return item?.id ?? item?.purchaseOrderLineId ?? ''; }
function lineName(line) { return line?.productName || line?.product?.name || line?.name || line?.sku || ('Producto ' + idOf(line)); }
function ordered(line) { return Number(line?.orderedQuantity ?? line?.quantityOrdered ?? line?.quantity ?? line?.requestedQuantity ?? 0); }
function received(line) { return Number(line?.receivedQuantity ?? line?.quantityReceived ?? line?.received ?? 0); }
function remaining(line) {
  const explicit = line?.remainingQuantity ?? line?.outstandingQuantity;
  return explicit == null ? Math.max(0, ordered(line) - received(line)) : Math.max(0, Number(explicit));
}

export default function ReceiptForm({ order, loading, onCancel, onSubmit }) {
  const orderLines = Array.isArray(order?.items) ? order.items : Array.isArray(order?.lines) ? order.lines : [];
  const [quantities, setQuantities] = useState(() => orderLines.map(() => '0'));
  const pending = useMemo(() => orderLines.filter((line, index) => Number(quantities[index] || 0) > 0 && idOf(line)), [orderLines, quantities]);

  function submit(event) {
    event.preventDefault();
    const items = pending.map(line => {
      const index = orderLines.indexOf(line);
      return { purchaseOrderLineId: idOf(line), quantity: Number(quantities[index]) };
    });
    if (!items.length || items.some(item => !Number.isFinite(item.quantity) || item.quantity <= 0)) return;
    onSubmit({ items });
  }

  return <form className="form-grid" onSubmit={submit}>
    <div className="form-hint wide">Orden: <strong>{order?.number || order?.code || order?.id}</strong>. Registra solo las cantidades físicamente recibidas. La contabilización actualiza el inventario en el backend.</div>
    {orderLines.length === 0 && <div className="alert error wide">La orden no contiene líneas visibles. Recarga la orden desde el servidor antes de recibir mercancía.</div>}
    {orderLines.map((line, index) => {
      const max = remaining(line);
      return <div className="receipt-line wide" key={idOf(line) || index}>
        <div><strong>{lineName(line)}</strong><small>Solicitado: {ordered(line)} · Recibido: {received(line)} · Pendiente: {max}</small></div>
        <label className="field"><span>Cantidad a recibir</span><input type="number" min="0" max={max} step="0.001" value={quantities[index] ?? '0'} onChange={event => setQuantities(current => current.map((value, qtyIndex) => qtyIndex === index ? event.target.value : value))} /></label>
      </div>;
    })}
    <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading || !pending.length || !orderLines.length}>{loading ? 'Contabilizando…' : 'Contabilizar recepción'}</button></div>
  </form>;
}

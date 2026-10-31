import { Search } from 'lucide-react';
import DataTable from '../../shared/ui/data-table/DataTable.jsx';
import Field from '../../shared/ui/forms/Field.jsx';
import InventoryOperations from '../../features/inventory/InventoryOperations.jsx';
import { number } from '../../utils/format.js';
import { productId, productName, productQuantity } from '../../entities/product/model/product.js';

export default function InventoryPage({
  products, balances, movements, warehouses, suppliers, inventoryFilter, setInventoryFilter,
  loading, stockTotal, inventoryValueLabel, onFilter, onChanged
}) {
  return <section className="panel">
    <form className="report-filters inventory-filters" onSubmit={onFilter}>
      <Field label="Almacén">
        <select value={inventoryFilter.warehouseId} onChange={event => setInventoryFilter(current => ({ ...current, warehouseId: event.target.value }))}>
          <option value="">Todos los almacenes</option>
          {warehouses.map(warehouse => <option key={productId(warehouse) || warehouse.code} value={productId(warehouse)}>{productName(warehouse)}</option>)}
        </select>
      </Field>
      <Field label="Producto">
        <select value={inventoryFilter.productId} onChange={event => setInventoryFilter(current => ({ ...current, productId: event.target.value }))}>
          <option value="">Todos los productos</option>
          {products.map(product => <option key={productId(product)} value={productId(product)}>{product.name} · {product.sku}</option>)}
        </select>
      </Field>
      <button className="button primary" disabled={loading}><Search size={16} /> Filtrar</button>
      <button type="button" className="button secondary" disabled={loading}
        onClick={() => {
          const reset = { warehouseId: '', productId: '' };
          setInventoryFilter(reset);
          onFilter({ preventDefault() {} }, reset);
        }}>Limpiar</button>
    </form>
    <div className="stat-strip">
      <div><span>Unidades</span><strong>{number(stockTotal)}</strong></div>
      <div><span>Valor estimado</span><strong>{inventoryValueLabel}</strong></div>
      <div><span>Movimientos recientes</span><strong>{number(movements.length)}</strong></div>
    </div>
    <h3 className="subheading">Saldos por producto</h3>
    <DataTable rows={balances} columns={[
      ['productName', 'Producto'], ['sku', 'SKU'], ['warehouseCode', 'Almacén'],
      ['onHand', 'Existencia'], ['reserved', 'Reservado'], ['available', 'Disponible']
    ]} empty="No hay saldos de inventario." caption="Saldos de inventario por producto y almacén" />
    <h3 className="subheading">Últimos movimientos</h3>
    <DataTable rows={movements.slice(0, 30)} columns={[
      ['type', 'Movimiento'],
      { key: 'productName', label: 'Producto', render: row => row.productName || products.find(product => String(productId(product)) === String(row.productId))?.name || row.sku || '—' },
      ['warehouseCode', 'Almacén'], ['quantity', 'Cantidad'], ['occurredAt', 'Fecha'], ['referenceType', 'Documento']
    ]} empty="No hay movimientos registrados." caption="Últimos movimientos de inventario" />
    <InventoryOperations products={products} warehouses={warehouses} suppliers={suppliers} onChanged={onChanged} />
  </section>;
}

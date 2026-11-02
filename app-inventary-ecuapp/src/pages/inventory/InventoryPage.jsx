import { useEffect, useMemo, useState } from 'react';
import { Activity, ArrowRight, Boxes, ClipboardCheck, PackageSearch, Search, ShieldCheck, Truck } from 'lucide-react';
import DataTable from '../../shared/ui/data-table/DataTable.jsx';
import Field from '../../shared/ui/forms/Field.jsx';
import InventoryOperations from '../../features/inventory/InventoryOperations.jsx';
import { api, listFrom } from '../../api';
import { number } from '../../utils/format.js';
import { productId, productName, productQuantity } from '../../entities/product/model/product.js';
import '../../features/operations/operational-forms.css';

const tabs = [
  ['command', 'Centro de mando', Activity],
  ['balances', 'Existencias', Boxes],
  ['movements', 'Movimientos / Kárdex', PackageSearch],
  ['operations', 'Operaciones', Truck],
  ['audit', 'Integridad y auditoría', ShieldCheck]
];

export default function InventoryPage({
  products, balances, movements, warehouses, suppliers, inventoryFilter, setInventoryFilter,
  loading, stockTotal, inventoryValueLabel, onFilter, onChanged
}) {
  const [tab, setTab] = useState('command');
  const [query, setQuery] = useState('');
  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState('');

  const balanceRows = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return balances;
    return balances.filter(row => [row.productName, row.sku, row.warehouseCode].some(value => String(value || '').toLowerCase().includes(needle)));
  }, [balances, query]);

  const movementRows = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return movements;
    return movements.filter(row => [row.productName, row.sku, row.referenceType, row.referenceId, row.correlationId, row.createdBy]
      .some(value => String(value || '').toLowerCase().includes(needle)));
  }, [movements, query]);

  useEffect(() => {
    let active = true;
    setSummaryLoading(true);
    setSummaryError('');
    api.commandCenter(inventoryFilter.warehouseId ? { warehouseId: inventoryFilter.warehouseId } : {})
      .then(data => { if (active) setSummary(data || null); })
      .catch(error => { if (active) setSummaryError(error?.message || 'No se pudo cargar el resumen de inventario.'); })
      .finally(() => { if (active) setSummaryLoading(false); });
    return () => { active = false; };
  }, [inventoryFilter.warehouseId]);

  const openTab = next => {
    setTab(next);
    window.scrollTo({ top: 0, behavior: 'auto' });
  };

  return <section className="panel inventory-command-page">
    <header className="inventory-page-header">
      <div>
        <p className="eyebrow">CONTROL OPERATIVO</p>
        <h2>Inventario</h2>
        <p>{inventoryFilter.warehouseId ? 'Alcance limitado al almacén seleccionado' : 'Vista general de todos los almacenes'} · {summaryLoading ? 'actualizando…' : 'datos del servidor'}</p>
      </div>
      <label className="inventory-global-search">
        <Search size={18} />
        <input value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar SKU, producto, documento o usuario" />
      </label>
    </header>

    <nav className="inventory-tabs" aria-label="Secciones de inventario">
      {tabs.map(([id, label, Icon]) => <button key={id} type="button" className={tab === id ? 'active' : ''} onClick={() => openTab(id)}>
        <Icon size={17} /> {label}
      </button>)}
    </nav>

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
      <button type="button" className="button secondary" disabled={loading} onClick={() => {
        const reset = { warehouseId: '', productId: '' };
        setInventoryFilter(reset);
        onFilter({ preventDefault() {} }, reset);
      }}>Limpiar</button>
    </form>

    {tab === 'command' && <div className="inventory-command-view">
      {summaryError && <div className="alert error" role="alert">{summaryError}</div>}
      <div className="stat-strip inventory-kpis">
        <div><span>Unidades en mano</span><strong>{number(summary?.onHand ?? stockTotal)}</strong></div>
        <div><span>Comprometido</span><strong>{number(summary?.reserved ?? 0)}</strong></div>
        <div><span>Disponible</span><strong>{number(summary?.available ?? 0)}</strong></div>
        <div><span>Valor estimado</span><strong>{inventoryValueLabel}</strong></div>
      </div>
      <div className="inventory-decisions-grid">
        <article className="inventory-work-panel">
          <div className="inventory-section-heading"><div><span>Lo que debe atender hoy</span><h3>Decisiones prioritarias</h3></div></div>
          {summary?.alerts?.length ? summary.alerts.map((alert, index) =>
            <button type="button" className={`inventory-alert ${alert.tone || 'warning'}`} key={`${alert.title}-${index}`} onClick={() => openTab(alert.actionTab || 'operations')}>
              <span className="inventory-alert-index">{index + 1}</span>
              <span><strong>{alert.title}</strong><small>{alert.detail}</small></span><ArrowRight size={18} />
            </button>) :
            <div className="inventory-empty"><ShieldCheck size={26} /><strong>No hay incidencias operativas conocidas</strong><span>Las alertas solo aparecen cuando existe evidencia en el servidor.</span></div>}
        </article>
        <article className="inventory-work-panel">
          <div className="inventory-section-heading"><div><span>Datos respaldados</span><h3>Tareas abiertas</h3></div></div>
          <div className="inventory-task-list">
            <button type="button" onClick={() => openTab('operations')}><ClipboardCheck /><span><strong>{summary?.openCounts ?? 0}</strong><small>Conteos abiertos</small></span><ArrowRight size={18} /></button>
            <button type="button" onClick={() => openTab('operations')}><Truck /><span><strong>{summary?.inTransitTransfers ?? 0}</strong><small>Traslados en tránsito</small></span><ArrowRight size={18} /></button>
            <button type="button" onClick={() => openTab('audit')}><ShieldCheck /><span><strong>{summary?.recentMovements ?? movements.length}</strong><small>Movimientos recientes</small></span><ArrowRight size={18} /></button>
          </div>
        </article>
      </div>
      <h3 className="subheading">Últimos movimientos</h3>
      <DataTable rows={movements.slice(0, 10)} columns={[
        ['type', 'Movimiento'],
        { key: 'productName', label: 'Producto', render: row => row.productName || products.find(product => String(productId(product)) === String(row.productId))?.name || row.sku || '—' },
        ['warehouseCode', 'Almacén'], ['quantity', 'Cantidad'], ['occurredAt', 'Fecha'], ['referenceType', 'Documento']
      ]} empty="No hay movimientos registrados." caption="Últimos movimientos de inventario" />
    </div>}

    {tab === 'balances' && <>
      <div className="inventory-section-heading"><div><span>Stock consultable por servidor</span><h3>Existencias</h3></div></div>
      <DataTable rows={balanceRows} columns={[['productName', 'Producto'], ['sku', 'SKU'], ['warehouseCode', 'Almacén'], ['onHand', 'Existencia'], ['reserved', 'Reservado'], ['available', 'Disponible']]} empty="No hay saldos de inventario." caption="Saldos de inventario por producto y almacén" />
    </>}

    {tab === 'movements' && <>
      <div className="inventory-section-heading"><div><span>Historial y trazabilidad</span><h3>Movimientos / Kárdex</h3></div></div>
      <DataTable rows={movementRows} columns={[
        ['type', 'Movimiento'],
        { key: 'productName', label: 'Producto', render: row => row.productName || products.find(product => String(productId(product)) === String(row.productId))?.name || row.sku || '—' },
        ['warehouseCode', 'Almacén'], ['quantity', 'Cantidad'], ['occurredAt', 'Fecha'], ['referenceType', 'Documento'], ['correlationId', 'Correlación']
      ]} empty="No hay movimientos registrados." caption="Movimientos de inventario y trazabilidad" />
    </>}

    {tab === 'operations' && <InventoryOperations products={products} warehouses={warehouses} suppliers={suppliers} onChanged={onChanged} />}

    {tab === 'audit' && <>
      <div className="inventory-section-heading"><div><span>Lectura restringida</span><h3>Integridad y auditoría</h3></div></div>
      <div className="inventory-empty"><ShieldCheck size={30} /><strong>Trazabilidad disponible por movimiento</strong><span>Seleccione un movimiento del listado para consultar su correlación, actor, documento y evidencia.</span></div>
      <DataTable rows={movementRows} columns={[['id', 'Movimiento'], ['createdBy', 'Actor'], ['correlationId', 'Correlación'], ['referenceType', 'Documento'], ['referenceId', 'ID documento'], ['occurredAt', 'Fecha']]} empty="No hay movimientos auditables." caption="Auditoría de movimientos de inventario" />
    </>}
  </section>;
}

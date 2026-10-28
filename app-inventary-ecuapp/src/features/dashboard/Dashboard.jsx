import { useEffect, useMemo, useState } from 'react';
import { ArrowDownToLine, ArrowUpFromLine, Ban, ChartArea, ChevronRight, CircleCheck, ClipboardCheck, FileClock, Inbox, MessageCircle, PackagePlus, PackageX, Table2, TriangleAlert, Truck } from 'lucide-react';
import { api, listFrom } from '../../api';
import AreaChart from '../../components/charts/AreaChart.jsx';
import BarList from '../../components/charts/BarList.jsx';
import Sparkline from '../../components/charts/Sparkline.jsx';
import { Skeleton, SkeletonBlock, SkeletonTiles } from '../../ui/Skeleton.jsx';
import { compactMoney, compactNumber, idOf, isInactive, isoDate, money, movementDirection, movementLabel, number, relativeTime, shortDate } from '../../utils/format.js';
import './dashboard.css';

const RANGES = [[7, '7 días'], [30, '30 días'], [90, '90 días']];

function rangeDates(days) {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - (days - 1));
  return { from: isoDate(from), to: isoDate(to) };
}

/** Rellena con ceros los días sin ventas para que el eje temporal sea continuo. */
function dailySeries(report, days) {
  const byDate = new Map(listFrom(report?.daily).map(day => [String(day.date), day]));
  const series = [];
  const cursor = new Date();
  cursor.setDate(cursor.getDate() - (days - 1));
  for (let index = 0; index < days; index++) {
    const key = isoDate(cursor);
    const day = byDate.get(key);
    series.push({ key, label: shortDate(cursor), value: Number(day?.grossSales || 0), detail: `${day?.salesCount || 0} ventas` });
    cursor.setDate(cursor.getDate() + 1);
  }
  return series;
}

function Tile({ label, value, detail, status, trend }) {
  return <div className="kpi-tile">
    <span className="kpi-label">{label}</span>
    <strong className="kpi-value">{value}</strong>
    <span className={'kpi-detail ' + (status || '')}>
      {status === 'warning' && <TriangleAlert size={13} aria-hidden="true" />}
      {status === 'good' && <CircleCheck size={13} aria-hidden="true" />}
      {detail}
    </span>
    {trend && <Sparkline values={trend} />}
  </div>;
}

function Card({ title, action, children, className = '' }) {
  return <section className={'dash-card ' + className}>
    <header className="dash-card-head"><h2>{title}</h2>{action}</header>
    {children}
  </section>;
}

export default function Dashboard({ data, ready, refreshing, refreshKey, whatsappPending, can, onNavigate, onStartWorkflow, onOpenProduct }) {
  const { products, balances, movements, purchases, sales, warehouses, valuation } = data;
  const [days, setDays] = useState(30);
  const [view, setView] = useState('chart');
  const [report, setReport] = useState({ loading: true, sales: null, lowStock: null });
  const canFinance = can('reports:financial');

  useEffect(() => {
    let active = true;
    const { from, to } = rangeDates(days);
    setReport(current => ({ ...current, loading: true }));
    Promise.allSettled([
      canFinance ? api.salesReport({ from, to }) : Promise.resolve(null),
      canFinance ? api.lowStockReport() : Promise.resolve(null)
    ]).then(([salesResult, lowResult]) => {
      if (!active) return;
      setReport({
        loading: false,
        sales: salesResult.status === 'fulfilled' ? salesResult.value : null,
        lowStock: lowResult.status === 'fulfilled' && lowResult.value ? listFrom(lowResult.value) : null
      });
    });
    return () => { active = false; };
  }, [days, canFinance, refreshKey]);

  const productName = useMemo(() => new Map(products.map(product => [String(idOf(product)), product.name])), [products]);
  const stockByProduct = useMemo(() => {
    const totals = new Map();
    balances.forEach(row => totals.set(String(row.productId), (totals.get(String(row.productId)) || 0) + Number(row.onHand || 0)));
    return totals;
  }, [balances]);
  const series = useMemo(() => dailySeries(report.sales, days), [report.sales, days]);
  const summary = report.sales?.summary;
  const units = balances.reduce((sum, row) => sum + Number(row.onHand || 0), 0);
  const totals = listFrom(valuation?.totals);
  const inventoryValue = !valuation ? '—' : totals.length > 1 ? 'Varias monedas' : compactMoney(totals[0]?.totalValue || 0);
  const outOfStock = products.filter(product => !isInactive(product) && (product.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD' && !(stockByProduct.get(String(idOf(product))) > 0));
  const lowStock = report.lowStock || [];
  const status = order => String(order.status || '').toUpperCase();

  const pending = [
    { id: 'approve', icon: ClipboardCheck, label: 'Compras por aprobar', count: purchases.filter(order => status(order) === 'SUBMITTED').length, go: () => onNavigate('purchases') },
    { id: 'receive', icon: Truck, label: 'Compras por recibir', count: purchases.filter(order => ['APPROVED', 'PARTIALLY_RECEIVED'].includes(status(order))).length, go: () => onNavigate('purchases') },
    { id: 'whatsapp', icon: MessageCircle, label: 'Pedidos WhatsApp por cobrar', count: whatsappPending || 0, go: () => onNavigate('whatsapp') },
    { id: 'cancel', icon: Ban, label: 'Anulaciones por aprobar', count: sales.filter(sale => status(sale) === 'CANCELLATION_REQUESTED').length, go: () => onNavigate('sales') },
    { id: 'drafts', icon: FileClock, label: 'Compras en borrador', count: purchases.filter(order => status(order) === 'DRAFT').length, go: () => onNavigate('purchases') },
    { id: 'empty', icon: PackageX, label: 'Productos sin existencias', count: outOfStock.length, go: () => onNavigate('products') }
  ].filter(task => task.count > 0);

  const byWarehouse = useMemo(() => {
    const totalsByWarehouse = new Map();
    balances.forEach(row => {
      const key = String(row.warehouseId || row.warehouseCode);
      const current = totalsByWarehouse.get(key) || { id: key, label: row.warehouseCode || 'Almacén', value: 0, products: new Set() };
      current.value += Number(row.onHand || 0);
      if (Number(row.onHand) > 0) current.products.add(String(row.productId));
      totalsByWarehouse.set(key, current);
    });
    const names = new Map(warehouses.map(warehouse => [String(idOf(warehouse)), warehouse.name]));
    return [...totalsByWarehouse.values()].sort((a, b) => b.value - a.value).slice(0, 6)
      .map(item => ({ id: item.id, label: names.get(item.id) || item.label, sublabel: `${item.products.size} productos`, value: item.value }));
  }, [balances, warehouses]);

  const topSellers = listFrom(report.sales?.products).slice().sort((a, b) => Number(b.netSales || 0) - Number(a.netSales || 0)).slice(0, 5)
    .map(row => ({ id: row.productId, label: row.productName || row.sku, sublabel: `${number(row.netQuantity)} unidades`, value: Number(row.netSales || 0) }));

  const recent = movements.slice().sort((a, b) => String(b.occurredAt || '').localeCompare(String(a.occurredAt || ''))).slice(0, 6);
  const margin = summary && Number(summary.netSales) > 0 ? Math.round(Number(summary.grossProfit || 0) / Number(summary.netSales) * 100) : null;

  if (!ready) return <div className="dashboard"><SkeletonTiles /><div className="dash-grid two-one"><SkeletonBlock height={280} /><SkeletonBlock height={280} /></div></div>;

  return <div className={'dashboard' + (refreshing ? ' is-refreshing' : '')}>
    <div className="dash-toolbar">
      <div className="segmented" role="group" aria-label="Periodo">
        {RANGES.map(([value, text]) => <button key={value} type="button" aria-pressed={days === value} onClick={() => setDays(value)}>{text}</button>)}
      </div>
      <div className="dash-actions">
        {can('sales:manage') && <button type="button" className="button primary" onClick={() => onStartWorkflow('sale')}><ArrowUpFromLine size={16} />Nueva venta</button>}
        {can('purchasing:manage') && <button type="button" className="button secondary" onClick={() => onStartWorkflow('purchase')}><ArrowDownToLine size={16} />Nueva compra</button>}
        {can('catalog:manage') && <button type="button" className="button secondary" onClick={() => onStartWorkflow('product')}><PackagePlus size={16} />Nuevo producto</button>}
      </div>
    </div>

    {report.loading && !report.sales && canFinance ? <SkeletonTiles /> : <div className="kpi-grid">
      <Tile label="Ventas netas" value={summary ? compactMoney(summary.netSales) : '—'} detail={summary ? `${number(summary.salesCount)} ventas` : 'Sin acceso a reportes'} trend={summary ? series.map(point => point.value) : null} />
      <Tile label="Utilidad bruta" value={summary ? compactMoney(summary.grossProfit) : '—'} detail={margin === null ? 'Sin ventas en el periodo' : `Margen ${margin} %`} />
      <Tile label="Valor del inventario" value={inventoryValue} detail={`${compactNumber(units)} unidades · ${warehouses.length} almacenes`} />
      <Tile label="Bajo mínimo" value={report.lowStock ? number(lowStock.length) : '—'} status={report.lowStock ? (lowStock.length ? 'warning' : 'good') : ''}
        detail={report.lowStock ? (lowStock.length ? 'Productos por reponer' : 'Existencias en orden') : 'Sin acceso a reportes'} />
    </div>}

    <div className="dash-grid two-one">
      <Card title="Ventas por día" action={<div className="segmented small" role="group" aria-label="Vista">
        <button type="button" aria-pressed={view === 'chart'} aria-label="Ver gráfico" onClick={() => setView('chart')}><ChartArea size={15} /></button>
        <button type="button" aria-pressed={view === 'table'} aria-label="Ver tabla" onClick={() => setView('table')}><Table2 size={15} /></button>
      </div>}>
        {!canFinance ? <div className="chart-empty">Tu rol no tiene acceso a los reportes de ventas.</div>
          : report.loading && !report.sales ? <Skeleton height={220} radius={10} />
          : view === 'chart' ? <AreaChart data={series} formatValue={money} formatAxis={compactMoney} label={`Ventas brutas diarias de los últimos ${days} días`} />
          : <div className="dash-table"><table><thead><tr><th scope="col">Fecha</th><th scope="col">Ventas</th><th scope="col">Ventas brutas</th></tr></thead>
            <tbody>{series.slice().reverse().map(point => <tr key={point.key}><td>{point.label}</td><td>{point.detail}</td><td>{money(point.value)}</td></tr>)}</tbody></table></div>}
      </Card>

      <Card title="Pendientes">
        {pending.length ? <ul className="task-list">{pending.map(task => <li key={task.id}>
          <button type="button" onClick={task.go}><span className="task-icon"><task.icon size={17} /></span><span className="task-label">{task.label}</span><span className="task-count">{task.count}</span><ChevronRight size={16} aria-hidden="true" /></button>
        </li>)}</ul> : <div className="chart-empty"><Inbox size={22} />Sin pendientes</div>}
      </Card>
    </div>

    <div className="dash-grid">
      <Card title="Reposición sugerida" action={lowStock.length > 0 && can('purchasing:manage') && <button type="button" className="text-button" onClick={() => onStartWorkflow('purchase')}>Crear compra <ChevronRight size={14} /></button>}>
        {!report.lowStock ? <div className="chart-empty">Tu rol no tiene acceso a los reportes de reposición.</div>
          : !lowStock.length ? <div className="chart-empty"><CircleCheck size={22} />Ningún producto bajo su mínimo</div>
          : <ul className="restock-list">{lowStock.slice(0, 6).map(item => <li key={item.productId + '-' + item.warehouseId}>
            <button type="button" onClick={() => onOpenProduct(item.productId)}>
              <span className="restock-name"><strong>{item.productName || item.sku}</strong><small>{item.warehouseCode} · mínimo {number(item.minimumQuantity)}</small></span>
              <span className="restock-status"><TriangleAlert size={13} aria-hidden="true" />{number(item.available)} disp.</span>
              <span className="restock-suggest">+{number(item.suggestedQuantity)}</span>
            </button>
          </li>)}</ul>}
      </Card>
      <Card title="Existencias por almacén" action={<button type="button" className="text-button" onClick={() => onNavigate('inventory')}>Inventario <ChevronRight size={14} /></button>}>
        <BarList items={byWarehouse} formatValue={compactNumber} label="Unidades en existencia por almacén" empty="Aún no hay existencias registradas." />
      </Card>
    </div>

    <div className="dash-grid">
      <Card title={`Más vendidos · ${days} días`}>
        {!canFinance ? <div className="chart-empty">Tu rol no tiene acceso a los reportes de ventas.</div>
          : <BarList items={topSellers} formatValue={compactMoney} label="Productos con mayores ventas netas" empty="Sin ventas en el periodo." onSelect={item => onOpenProduct(item.id)} />}
      </Card>
      <Card title="Movimientos recientes" action={<button type="button" className="text-button" onClick={() => onNavigate('inventory')}>Ver todos <ChevronRight size={14} /></button>}>
        {recent.length ? <ul className="movement-list">{recent.map((movement, index) => {
          const direction = movementDirection(movement.type);
          return <li key={movement.id || index}>
            <span className={'movement-sign ' + (direction > 0 ? 'in' : direction < 0 ? 'out' : 'neutral')} aria-hidden="true">{direction > 0 ? '+' : direction < 0 ? '−' : '•'}</span>
            <span className="movement-copy"><strong>{productName.get(String(movement.productId)) || movement.sku || 'Producto'}</strong><small>{movementLabel(movement.type)} · {movement.warehouseCode}</small></span>
            <span className="movement-meta"><strong>{direction < 0 ? '−' : direction > 0 ? '+' : ''}{number(movement.quantity)}</strong><small>{relativeTime(movement.occurredAt)}</small></span>
          </li>;
        })}</ul> : <div className="chart-empty">Aún no hay movimientos.</div>}
      </Card>
    </div>
  </div>;
}

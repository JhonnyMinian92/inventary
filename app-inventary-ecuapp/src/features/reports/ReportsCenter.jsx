import { useMemo, useState } from 'react';
import { Activity, CalendarRange, Download, FileSpreadsheet, RefreshCw, Search, ShieldAlert } from 'lucide-react';
import { api, listFrom } from '../../api';

const REPORT_TABS = [
  { id: 'balance', label: 'Balance de inventario' },
  { id: 'sales', label: 'Ventas y margen' },
  { id: 'purchases', label: 'Compras y devoluciones' },
  { id: 'kardex', label: 'Kardex' },
  { id: 'valuation', label: 'Valoración de stock' },
  { id: 'low-stock', label: 'Stock bajo' }
];

function localDate(date = new Date()) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}
function startDate() {
  const date = new Date();
  date.setDate(date.getDate() - 30);
  return localDate(date);
}
function idOf(item) { return item?.id ?? item?.productId ?? item?.warehouseId ?? ''; }
function money(value, currency = 'USD') {
  if (value === null || value === undefined || value === '') return '—';
  try {
    return new Intl.NumberFormat('es-EC', { style: 'currency', currency: currency || 'USD' }).format(Number(value));
  } catch {
    return Number(value).toLocaleString('es-EC', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + currency;
  }
}
function number(value) {
  if (value === null || value === undefined || value === '') return '—';
  return Number(value).toLocaleString('es-EC', { maximumFractionDigits: 3 });
}
function dateTime(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleString('es-EC');
}
function titleCase(value) {
  return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase());
}
function formatError(error) {
  if (error?.status === 403) {
    return { kind: 'permission', message: 'Tu usuario no tiene el permiso reports:financial requerido para consultar estos informes. Solicita acceso al administrador; no se mostrarán datos financieros sin autorización.' };
  }
  if (error?.status === 401) {
    return { kind: 'permission', message: 'La sesión venció o ya no es válida. Inicia sesión nuevamente.' };
  }
  return { kind: 'error', message: error?.message || 'No se pudo generar el informe. Verifica los filtros y vuelve a intentarlo.' };
}
function sumByCurrency(rows, key) {
  const sums = new Map();
  (rows || []).forEach(row => {
    const currency = row.currency || 'USD';
    sums.set(currency, (sums.get(currency) || 0) + Number(row[key] || 0));
  });
  return [...sums.entries()].map(([currency, amount]) => ({ currency, amount }));
}
function currencyText(rows, key) {
  const totals = sumByCurrency(rows, key);
  if (!totals.length) return money(0);
  return totals.map(item => money(item.amount, item.currency)).join(' · ');
}
function marginText(rows) {
  const grouped = new Map();
  (rows || []).forEach(row => {
    const currency = row.currency || 'USD';
    const values = grouped.get(currency) || { sales: 0, profit: 0 };
    values.sales += Number(row.salesAmount || 0);
    values.profit += Number(row.grossProfit || 0);
    grouped.set(currency, values);
  });
  return [...grouped.entries()].map(([currency, values]) => currency + ': ' + (values.sales > 0 ? (values.profit / values.sales * 100).toLocaleString('es-EC', { maximumFractionDigits: 2 }) + '%' : '—')).join(' · ') || '—';
}
function asCsvValue(value) {
  const text = value === null || value === undefined ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value);
  return '"' + text.replaceAll('"', '""') + '"';
}
function downloadCsv(fileName, columns, rows) {
  const csv = [columns.map(column => asCsvValue(column.label)).join(','), ...(rows || []).map(row => columns.map(column => asCsvValue(typeof column.csvValue === 'function' ? column.csvValue(row) : row[column.key])).join(','))].join('\r\n');
  const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
function Metrics({ items }) {
  if (!items?.length) return null;
  return <div className="report-metrics">
    {items.map((item, index) => <div className="report-metric" key={item.label + index}>
      <span>{item.label}</span><strong>{item.value}</strong>{item.detail && <small>{item.detail}</small>}
    </div>)}
  </div>;
}
function ReportTable({ rows, columns, caption, empty }) {
  if (!rows?.length) return <div className="empty-state" role="status"><FileSpreadsheet size={22}/><span>{empty || 'No hay filas para este informe con los filtros seleccionados.'}</span></div>;
  return <div className="table-wrap report-table-wrap">
    <table aria-label={caption}>
      <caption className="visually-hidden">{caption}</caption>
      <thead><tr>{columns.map(column => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead>
      <tbody>{rows.map((row, index) => <tr key={idOf(row) || row.movementId || row.saleItemId || row.receiptLineId || index}>
        {columns.map(column => {
          const value = column.render ? column.render(row) : column.format === 'money' ? money(row[column.key], row.currency) : column.format === 'quantity' ? number(row[column.key]) : column.format === 'date' ? dateTime(row[column.key]) : column.format === 'status' ? titleCase(row[column.key]) : row[column.key] ?? '—';
          return <td key={column.key}>{value}</td>;
        })}
      </tr>)}</tbody>
    </table>
  </div>;
}

export default function ReportsCenter({ warehouses = [], products = [], suppliers = [], onUnauthorized }) {
  const [activeTab, setActiveTab] = useState('balance');
  const [filters, setFilters] = useState(() => ({ from: startDate(), to: localDate(), warehouseId: '', productId: '', supplierId: '', zone: 'America/Guayaquil' }));
  const [results, setResults] = useState({});
  const [queried, setQueried] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastRun, setLastRun] = useState({});
  const data = results[activeTab];
  const resultReady = Boolean(queried[activeTab] && data !== undefined);
  const today = localDate();
  const reportHasRange = ['balance', 'sales', 'purchases', 'kardex'].includes(activeTab);
  const columns = useMemo(() => {
    if (activeTab === 'balance') return [
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' },
      { key: 'openingStock', label: 'Saldo inicial', format: 'quantity' },
      { key: 'purchasedQuantity', label: 'Compra neta', format: 'quantity' },
      { key: 'purchaseAmount', label: 'Importe compras', format: 'money' },
      { key: 'soldQuantity', label: 'Venta neta', format: 'quantity' },
      { key: 'salesAmount', label: 'Importe ventas', format: 'money' },
      { key: 'closingStock', label: 'Saldo al cierre', format: 'quantity' },
      { key: 'currentStock', label: 'Stock actual', format: 'quantity' },
      { key: 'currentReserved', label: 'Reservado', format: 'quantity' },
      { key: 'currentAvailable', label: 'Disponible', format: 'quantity' },
      { key: 'openingValue', label: 'Valor inicial', format: 'money' },
      { key: 'closingValue', label: 'Valor al cierre', format: 'money' },
      { key: 'costOfSales', label: 'Costo de ventas', format: 'money' },
      { key: 'grossProfit', label: 'Utilidad bruta', format: 'money' },
      { key: 'margin', label: 'Margen bruto', render: row => Number(row.salesAmount) > 0 ? (Number(row.grossProfit || 0) / Number(row.salesAmount) * 100).toLocaleString('es-EC', { maximumFractionDigits: 2 }) + '%' : '—', csvValue: row => Number(row.salesAmount) > 0 ? (Number(row.grossProfit || 0) / Number(row.salesAmount) * 100).toFixed(2) + '%' : '' }
    ];
    if (activeTab === 'sales') return [
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' },
      { key: 'netQuantity', label: 'Cantidad neta', format: 'quantity' },
      { key: 'netSales', label: 'Ventas netas', format: 'money' },
      { key: 'costOfSales', label: 'Costo de ventas', format: 'money' },
      { key: 'grossProfit', label: 'Utilidad bruta', format: 'money' }
    ];
    if (activeTab === 'purchases') return [
      { key: 'supplierName', label: 'Proveedor' }, { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' },
      { key: 'receivedQuantity', label: 'Recibido', format: 'quantity' },
      { key: 'receivedAmount', label: 'Importe recibido', format: 'money' },
      { key: 'returnedQuantity', label: 'Devuelto', format: 'quantity' },
      { key: 'returnedAmount', label: 'Importe devuelto', format: 'money' }
    ];
    if (activeTab === 'kardex') return [
      { key: 'occurredAt', label: 'Fecha', format: 'date' }, { key: 'movementType', label: 'Movimiento', format: 'status' },
      { key: 'referenceType', label: 'Documento', render: row => titleCase(row.referenceType) },
      { key: 'referenceId', label: 'Referencia' }, { key: 'notes', label: 'Detalle' },
      { key: 'quantityIn', label: 'Entrada', format: 'quantity' }, { key: 'quantityOut', label: 'Salida', format: 'quantity' },
      { key: 'balanceQuantity', label: 'Saldo', format: 'quantity' }, { key: 'valueIn', label: 'Valor entrada', format: 'money' },
      { key: 'valueOut', label: 'Valor salida', format: 'money' }, { key: 'balanceValue', label: 'Valor saldo', format: 'money' },
      { key: 'createdBy', label: 'Registrado por' }
    ];
    if (activeTab === 'valuation') return [
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' }, { key: 'warehouseCode', label: 'Almacén' },
      { key: 'currency', label: 'Moneda' }, { key: 'onHand', label: 'Existencia', format: 'quantity' },
      { key: 'valuedQuantity', label: 'Cantidad valorada', format: 'quantity' },
      { key: 'averageCost', label: 'Costo promedio', format: 'money' }, { key: 'totalValue', label: 'Valor total', format: 'money' },
      { key: 'reconciled', label: 'Conciliado', render: row => row.reconciled ? 'Sí' : 'Revisar' }
    ];
    return [
      { key: 'sku', label: 'SKU' }, { key: 'productName', label: 'Producto' }, { key: 'warehouseCode', label: 'Almacén' },
      { key: 'available', label: 'Disponible', format: 'quantity' }, { key: 'minimumQuantity', label: 'Mínimo', format: 'quantity' },
      { key: 'targetQuantity', label: 'Objetivo', format: 'quantity' }, { key: 'suggestedQuantity', label: 'Reponer', format: 'quantity' },
      { key: 'leadTimeDays', label: 'Plazo (días)' }, { key: 'preferredSupplierName', label: 'Proveedor sugerido' }
    ];
  }, [activeTab]);

  function updateFilter(key, value) {
    setFilters(current => ({ ...current, [key]: value }));
  }

  async function runReport(event, requestedPage = 0) {
    event?.preventDefault?.();
    if (loading) return;
    if (reportHasRange) {
      if (!filters.from || !filters.to) {
        setError({ kind: 'error', message: 'Debes indicar las dos fechas del periodo.' });
        return;
      }
      if (filters.from > filters.to) {
        setError({ kind: 'error', message: 'La fecha inicial no puede ser posterior a la fecha final.' });
        return;
      }
      if (filters.to > today) {
        setError({ kind: 'error', message: 'La fecha final no puede ser futura.' });
        return;
      }
      const days = Math.round((new Date(filters.to + 'T00:00:00Z') - new Date(filters.from + 'T00:00:00Z')) / 86400000);
      if (days >= 366) {
        setError({ kind: 'error', message: 'El backend admite periodos de hasta 366 días incluyendo las fechas inicial y final.' });
        return;
      }
    }
    if (activeTab === 'balance' && !filters.warehouseId) {
      setError({ kind: 'error', message: 'Selecciona un almacén para calcular el balance.' });
      return;
    }
    if (activeTab === 'kardex' && (!filters.warehouseId || !filters.productId)) {
      setError({ kind: 'error', message: 'Selecciona un almacén y un producto para consultar el kardex.' });
      return;
    }

    setLoading(true);
    setError(null);
    try {
      let response;
      const range = { from: filters.from, to: filters.to, zone: filters.zone };
      if (activeTab === 'balance') {
        const params = { ...range, warehouseId: filters.warehouseId, page: requestedPage, size: 100 };
        if (filters.productId) params.productId = filters.productId;
        response = await api.inventoryBalanceReport(params);
      } else if (activeTab === 'sales') {
        const params = { ...range };
        if (filters.warehouseId) params.warehouseId = filters.warehouseId;
        response = await api.salesReport(params);
      } else if (activeTab === 'purchases') {
        const params = { ...range };
        if (filters.warehouseId) params.warehouseId = filters.warehouseId;
        if (filters.supplierId) params.supplierId = filters.supplierId;
        response = await api.purchasesReport(params);
      } else if (activeTab === 'kardex') {
        response = await api.kardexReport({ ...range, warehouseId: filters.warehouseId, productId: filters.productId });
      } else if (activeTab === 'valuation') {
        const params = { page: requestedPage, size: 100, includeEmpty: false };
        if (filters.warehouseId) params.warehouseId = filters.warehouseId;
        if (filters.productId) params.productId = filters.productId;
        response = await api.inventoryValuationReport(params);
      } else {
        response = await api.lowStockReport(filters.warehouseId ? { warehouseId: filters.warehouseId } : {});
      }
      setResults(current => ({ ...current, [activeTab]: response }));
      setQueried(current => ({ ...current, [activeTab]: true }));
      setLastRun(current => ({ ...current, [activeTab]: new Date().toISOString() }));
    } catch (err) {
      const nextError = formatError(err);
      setError(nextError);
      setResults(current => nextError.kind === 'permission' ? {} : ({ ...current, [activeTab]: undefined }));
      setQueried(current => nextError.kind === 'permission' ? {} : ({ ...current, [activeTab]: false }));
      if (err?.status === 401) onUnauthorized?.();
    } finally {
      setLoading(false);
    }
  }

  const tableRows = activeTab === 'balance' ? listFrom(data?.items)
    : activeTab === 'sales' ? listFrom(data?.products)
    : activeTab === 'purchases' ? listFrom(data?.items)
    : activeTab === 'kardex' ? listFrom(data?.movements)
    : activeTab === 'valuation' ? listFrom(data?.items)
    : Array.isArray(data) ? data : listFrom(data);
  const exportData = () => {
    const prefix = 'inventary-' + activeTab + '-' + localDate();
    downloadCsv(prefix + '.csv', columns, tableRows);
  };

  function renderFilters() {
    return <>
      {reportHasRange && <label className="field"><span>Desde<b> *</b></span><input aria-label="Fecha inicial" type="date" required max={today} value={filters.from} onChange={event => updateFilter('from', event.target.value)} /></label>}
      {reportHasRange && <label className="field"><span>Hasta<b> *</b></span><input aria-label="Fecha final" type="date" required min={filters.from || undefined} max={today} value={filters.to} onChange={event => updateFilter('to', event.target.value)} /></label>}
      {['balance','kardex','valuation','low-stock','sales','purchases'].includes(activeTab) && <label className="field"><span>Almacén{activeTab === 'balance' || activeTab === 'kardex' ? <b> *</b> : null}</span><select aria-label="Filtrar por almacén" required={activeTab === 'balance' || activeTab === 'kardex'} value={filters.warehouseId} onChange={event => updateFilter('warehouseId', event.target.value)}><option value="">Todos los almacenes</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.code || item.name || idOf(item)}</option>)}</select></label>}
      {['balance','kardex','valuation'].includes(activeTab) && <label className="field"><span>Producto{activeTab === 'kardex' ? <b> *</b> : null}</span><select aria-label="Filtrar por producto" required={activeTab === 'kardex'} value={filters.productId} onChange={event => updateFilter('productId', event.target.value)}><option value="">Todos los productos</option>{products.map(item => <option key={idOf(item)} value={idOf(item)}>{item.sku ? item.sku + ' · ' : ''}{item.name}</option>)}</select></label>}
      {activeTab === 'purchases' && <label className="field"><span>Proveedor</span><select aria-label="Filtrar por proveedor" value={filters.supplierId} onChange={event => updateFilter('supplierId', event.target.value)}><option value="">Todos los proveedores</option>{suppliers.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name || item.code}</option>)}</select></label>}
      {reportHasRange && <label className="field"><span>Zona horaria</span><select aria-label="Zona horaria del informe" value={filters.zone} onChange={event => updateFilter('zone', event.target.value)}><option value="America/Guayaquil">Ecuador continental/Guayaquil</option><option value="America/Galapagos">Galápagos</option><option value="UTC">UTC</option><option value="America/New_York">Nueva York</option></select></label>}
    </>;
  }

  function renderResult() {
    if (!resultReady) return <div className="empty-state report-empty"><CalendarRange size={26}/><strong>Consulta el informe para ver resultados</strong><span>Los datos se solicitan directamente al backend cuando envías los filtros.</span></div>;
    if (activeTab === 'balance') {
      const rows = listFrom(data?.items);
      if (!rows.length) return <div className="empty-state report-empty"><FileSpreadsheet size={26}/><strong>Sin movimientos o saldos en el periodo</strong><span>Prueba otro rango o selecciona otro producto. El backend no inventa filas sin actividad histórica.</span></div>;
      return <>
        <Metrics items={[
          { label: 'Valor al inicio', value: currencyText(rows, 'openingValue') },
          { label: 'Compras netas', value: currencyText(rows, 'purchaseAmount') },
          { label: 'Ventas netas', value: currencyText(rows, 'salesAmount') },
          { label: 'Costo de ventas', value: currencyText(rows, 'costOfSales') },
          { label: 'Valor al cierre', value: currencyText(rows, 'closingValue') },
          { label: 'Utilidad bruta', value: currencyText(rows, 'grossProfit') },
          { label: 'Margen bruto', value: marginText(rows) }
        ]}/>
        <div className="report-subheading"><div><h3>Balance por producto</h3><p>Saldo al inicio del periodo, compras y ventas netas, y saldo al cierre. El stock actual puede diferir porque incluye movimientos posteriores al periodo.</p></div><span className="muted">{data.totalElements ?? rows.length} productos</span></div>
        <ReportTable rows={rows} columns={columns} caption="Balance de inventario por producto" empty="Sin filas para los filtros seleccionados."/>
        <p className="report-footnote">Zona horaria: {data.zone || filters.zone}. Generado: {dateTime(data.generatedAt)}. Página {Number(data.page || 0) + 1} de {data.totalPages || 1}.</p>
      </>;
    }
    if (activeTab === 'sales') {
      const summary = data?.summary || {};
      const rows = listFrom(data?.products);
      return <>
        <Metrics items={[
          { label: 'Ventas registradas', value: number(summary.salesCount || 0) },
          { label: 'Anulaciones aprobadas', value: number(summary.cancelledCount || 0) },
          { label: 'Ventas brutas', value: money(summary.grossSales) },
          { label: 'Anulaciones', value: money(summary.cancellations) },
          { label: 'Devoluciones', value: money(summary.returns) },
          { label: 'Ventas netas', value: money(summary.netSales) },
          { label: 'Costo de ventas', value: money(summary.costOfSales) },
          { label: 'Utilidad bruta', value: money(summary.grossProfit) }
        ]}/>
        <div className="report-subheading"><div><h3>Ventas diarias</h3><p>Totales brutos por día dentro del periodo.</p></div></div>
        <ReportTable rows={listFrom(data?.daily)} columns={[{key:'date',label:'Fecha'},{key:'salesCount',label:'Ventas'},{key:'grossSales',label:'Ventas brutas',format:'money'}]} caption="Resumen diario de ventas" empty="No hubo ventas en el periodo."/>
        <div className="report-subheading"><div><h3>Resultados por producto</h3><p>Las cantidades e importes son netos de anulaciones y devoluciones según la API.</p></div></div>
        <ReportTable rows={rows} columns={columns} caption="Ventas netas por producto" empty="No hay ventas por producto para este periodo."/>
      </>;
    }
    if (activeTab === 'purchases') {
      const summary = data?.summary || {};
      const rows = listFrom(data?.items);
      return <>
        <Metrics items={[
          { label: 'Recepciones contabilizadas', value: number(summary.receiptsCount || 0) },
          { label: 'Devoluciones a proveedor', value: number(summary.returnsCount || 0) },
          { label: 'Importe recibido', value: money(summary.receivedAmount) },
          { label: 'Importe devuelto', value: money(summary.returnedAmount) },
          { label: 'Compras netas', value: money(summary.netAmount) }
        ]}/>
        <ReportTable rows={rows} columns={columns} caption="Recepciones y devoluciones por proveedor y producto" empty="No hay recepciones contabilizadas en este periodo."/>
      </>;
    }
    if (activeTab === 'kardex') {
      const rows = listFrom(data?.movements);
      return <>
        <div className="report-kardex-heading"><div><span>Producto</span><strong>{data.productName || data.sku || filters.productId}</strong></div><div><span>Almacén</span><strong>{data.warehouseCode || filters.warehouseId}</strong></div><div><span>Saldo inicial</span><strong>{number(data.openingQuantity)} · {money(data.openingValue)}</strong></div><div><span>Saldo final</span><strong>{number(data.closingQuantity)} · {money(data.closingValue)}</strong></div></div>
        <ReportTable rows={rows} columns={columns} caption="Kardex cronológico de inventario" empty="No hay movimientos del producto en el periodo seleccionado."/>
      </>;
    }
    if (activeTab === 'valuation') {
      const rows = listFrom(data?.items);
      return <>
        <div className="report-subheading"><div><h3>Valor total por moneda</h3><p>Método de costeo: <strong>{titleCase(data.method)}</strong>. Los importes vienen de la valoración financiera del backend.</p></div><span className="muted">Generado: {dateTime(data.generatedAt)}</span></div>
        <Metrics items={(data?.totals || []).map(item => ({ label: 'Total en ' + item.currency, value: money(item.totalValue, item.currency), detail: 'Existencia: ' + number(item.onHand) }))}/>
        <ReportTable rows={rows} columns={columns} caption="Valoración del inventario por producto y almacén" empty="No hay existencias valoradas que coincidan con los filtros."/>
        {rows.some(row => row.reconciled === false) && <div className="alert warning" role="status"><ShieldAlert size={18}/><span>Algunas filas no están conciliadas. Revisa los movimientos y el libro de valoración antes de utilizar estos saldos en un cierre financiero.</span></div>}
      </>;
    }
    const rows = Array.isArray(data) ? data : listFrom(data);
    return <>
      <Metrics items={[{ label: 'Productos bajo mínimo', value: number(rows.length), detail: 'Con sugerencia de reposición activa' }]}/>
      <ReportTable rows={rows} columns={columns} caption="Sugerencias de reposición" empty="No hay productos bajo el mínimo en el almacén seleccionado."/>
    </>;
  }

  return <section className="panel reports-center" aria-labelledby="reports-title">
    <div className="panel-heading">
      <div className="panel-title-icon"><span><FileSpreadsheet size={20}/></span><div><h2 id="reports-title">Centro de reportes</h2><p>Balance histórico, ventas netas, compras, kardex y valoración contable usando los DTO reales del backend.</p></div></div>
      {resultReady && tableRows.length > 0 && <button className="button secondary" type="button" onClick={exportData}><Download size={16}/> Exportar CSV</button>}
    </div>
    <div className="report-tabs" role="tablist" aria-label="Informes de inventario">
      {REPORT_TABS.map(tab => <button key={tab.id} type="button" role="tab" aria-selected={activeTab === tab.id} aria-controls="report-results" id={'report-tab-' + tab.id} className={activeTab === tab.id ? 'report-tab active' : 'report-tab'} onClick={() => { setActiveTab(tab.id); setError(null); }}>{tab.label}</button>)}
    </div>
    {error && <div className={'alert ' + (error.kind === 'permission' ? 'warning' : 'error')} role="alert" aria-live="assertive"><ShieldAlert size={18}/><span>{error.message}</span><button type="button" aria-label="Cerrar mensaje" onClick={() => setError(null)}>×</button></div>}
    <form className="report-filters report-center-filters" onSubmit={runReport} aria-label="Filtros del informe">
      {renderFilters()}
      <div className="report-filter-actions"><button className="button primary" type="submit" disabled={loading}><Search size={16}/>{loading ? 'Consultando…' : 'Consultar informe'}</button><button className="button secondary" type="button" disabled={loading} onClick={() => { setResults(current => ({ ...current, [activeTab]: undefined })); setQueried(current => ({ ...current, [activeTab]: false })); setError(null); }}><RefreshCw size={16}/> Limpiar resultado</button></div>
    </form>
    <div id="report-results" role="tabpanel" aria-labelledby={'report-tab-' + activeTab} aria-busy={loading} className="report-results">
      {loading ? <div className="empty-state" role="status"><Activity size={22}/><span>Consultando el backend y calculando el informe…</span></div> : renderResult()}
      {resultReady && !loading && ['balance','valuation'].includes(activeTab) && Number(data?.totalPages || 0) > 1 && <nav className="report-pagination" aria-label="Páginas del informe"><button type="button" className="button secondary" disabled={loading || Number(data.page || 0) <= 0} onClick={() => void runReport(null, Number(data.page || 0) - 1)}>Página anterior</button><span>Página {Number(data.page || 0) + 1} de {data.totalPages} · {data.totalElements} filas</span><button type="button" className="button secondary" disabled={loading || Number(data.page || 0) + 1 >= Number(data.totalPages)} onClick={() => void runReport(null, Number(data.page || 0) + 1)}>Página siguiente</button></nav>}
      {lastRun[activeTab] && resultReady && <p className="report-footnote">Última consulta: {dateTime(lastRun[activeTab])}. Los datos reflejan el resultado entregado por la API en ese momento.</p>}
    </div>
    <div className="report-permission-note"><ShieldAlert size={15}/><span>Los informes financieros requieren el permiso <code>reports:financial</code>. Las respuestas 403 se muestran como falta de autorización; no se reemplazan por datos simulados.</span></div>
  </section>;
}

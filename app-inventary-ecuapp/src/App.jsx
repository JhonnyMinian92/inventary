import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, ArrowDownToLine, ArrowUpFromLine, Boxes, ChartNoAxesCombined, ChevronRight, CircleAlert, ClipboardList, Layers3, LogOut, PackagePlus, Plus, RefreshCw, Search, Tags, Warehouse } from 'lucide-react';
import { api, getToken, listFrom, setToken } from './api';
import ProductForm from './features/catalog/ProductForm.jsx';
import CategoryForm from './features/catalog/CategoryForm.jsx';
import PurchaseForm from './features/purchasing/PurchaseForm.jsx';
import ReceiptForm from './features/purchasing/ReceiptForm.jsx';
import SaleForm from './features/sales/SaleForm.jsx';
import OperationsCatalogs from './features/operations/OperationsCatalogs.jsx';
import InventoryOperations from './features/inventory/InventoryOperations.jsx';
import ReportsCenter from './features/reports/ReportsCenter.jsx';
import Overview from './features/dashboard/Overview.jsx';
import DataTable from './components/DataTable.jsx';
import PurchaseTable from './features/purchasing/PurchaseTable.jsx';
import SalesTable from './features/sales/SalesTable.jsx';
import { ProductsTable, CategoriesTable } from './features/catalog/CatalogTables.jsx';
import useAccessibleDialog from './hooks/useAccessibleDialog.js';
import PurchaseReturns from './features/purchasing/PurchaseReturns.jsx';
import SalesReturns from './features/sales/SalesReturns.jsx';

const nav = [
  { id: 'overview', label: 'Resumen', icon: ChartNoAxesCombined },
  { id: 'products', label: 'Productos', icon: Boxes },
  { id: 'categories', label: 'Categorías', icon: Tags },
  { id: 'partners', label: 'Almacenes y contactos', icon: Warehouse },
  { id: 'purchases', label: 'Compras', icon: ArrowDownToLine },
  { id: 'sales', label: 'Ventas', icon: ArrowUpFromLine },
  { id: 'inventory', label: 'Inventario', icon: Warehouse },
  { id: 'reports', label: 'Reportes', icon: ClipboardList }
];

function titleCase(value = '') { return String(value).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()); }
function money(value) { return new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(value || 0)); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }
function pickId(x) { return x?.id ?? x?.productId ?? x?.categoryId ?? x?.warehouseId ?? ''; }
function pickName(x) { return x?.name ?? x?.description ?? x?.sku ?? String(pickId(x)); }
function quantity(x) { return x?.onHand ?? x?.quantity ?? x?.available ?? x?.stock ?? 0; }

function App() {
  const [token, setTokenState] = useState(getToken());
  const [user, setUser] = useState(null);
  const [section, setSection] = useState('overview');
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [units, setUnits] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [balances, setBalances] = useState([]);
  const [movements, setMovements] = useState([]);
  const [purchases, setPurchases] = useState([]);
  const [sales, setSales] = useState([]);
  const [valuation, setValuation] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [purchaseToCancel, setPurchaseToCancel] = useState(null);
  const [statusChangeTarget, setStatusChangeTarget] = useState(null);
  const [saleToCancel, setSaleToCancel] = useState(null);
  const [saleCancelReason, setSaleCancelReason] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [apiStatus, setApiStatus] = useState('checking');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState('');
  const [editing, setEditing] = useState(null);
  const [inventoryFilter, setInventoryFilter] = useState({ warehouseId: '', productId: '' });

  const loadData = useCallback(async () => {
    setLoading(true); setError('');
    const tasks = [
      ['products', api.products({ size: 100 })], ['categories', api.categories({ size: 100 })], ['units', api.units()],
      ['warehouses', api.warehouses({ size: 100 })], ['suppliers', api.suppliers({ size: 100 })], ['customers', api.customers({ size: 100 })], ['balances', api.balances({ size: 100 })], ['movements', api.movements({ size: 100 })],
      ['purchases', api.purchaseOrders({ size: 100 })], ['sales', api.sales({ size: 100 })], ['valuation', api.inventoryValuationReport({ size: 100 })]
    ];
    const results = await Promise.allSettled(tasks.map(t => t[1]));
    const unauthorized = results.some(result => result.status === 'rejected' && result.reason?.status === 401);
    if (unauthorized) {
      setApiStatus('error');
      setToken('');
      setTokenState('');
      setUser(null);
      setError('La sesión expiró o ya no es válida. Inicia sesión nuevamente.');
      setLoading(false);
      return;
    }
    setApiStatus(results.some(result => result.status === 'fulfilled') ? 'connected' : 'error');
    const failed = [];
    results.forEach((result, i) => {
      const [key] = tasks[i];
      if (key === 'valuation') {
        setValuation(result.status === 'fulfilled' ? result.value : null);
        return;
      }
      if (result.status === 'fulfilled') {
        const value = listFrom(result.value);
        ({ products: setProducts, categories: setCategories, units: setUnits, warehouses: setWarehouses, suppliers: setSuppliers, customers: setCustomers, balances: setBalances, movements: setMovements, purchases: setPurchases, sales: setSales })[key](value);
      } else if (['products', 'categories', 'warehouses', 'balances'].includes(key)) failed.push(`${key}: ${result.reason?.message || 'error'}`);
    });
    if (failed.length) setError(`No se pudieron cargar algunos datos. ${failed.join(' · ')}`);
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!token) return;
    api.me().then(setUser).catch(e => { if (e.status === 401) { setToken(''); setTokenState(''); setUser(null); setError('La sesión expiró o ya no es válida. Inicia sesión nuevamente.'); } else setError(e.message); });
    loadData();
  }, [token, loadData]);

  const visibleProducts = useMemo(() => products.filter(p => [p.name, p.sku, p.code, p.description].some(v => String(v || '').toLowerCase().includes(search.toLowerCase()))), [products, search]);
  const stockTotal = balances.reduce((sum, row) => sum + Number(quantity(row)), 0);
  const valuationTotals = Array.isArray(valuation?.totals) ? valuation.totals : [];
  const inventoryValueLabel = !valuation ? '—' : valuationTotals.length === 1 ? money(valuationTotals[0].totalValue) : valuationTotals.length > 1 ? 'Varias monedas' : money(0);

  async function runAction(action, success) {
    setLoading(true); setError(''); setNotice('');
    try { await action(); setNotice(success); setModal(''); setEditing(null); await loadData(); return true; }
    catch (e) {
      if (e.status === 401) {
        setToken('');
        setTokenState('');
        setUser(null);
        setError('La sesión expiró o ya no es válida. Inicia sesión nuevamente.');
      } else {
        setError(e.message || 'No se pudo completar la operación.');
      }
      return false;
    }
    finally { setLoading(false); }
  }

  async function submitProduct(form) {
    if (!form.categoryId) {
      setError('Selecciona una categoría. El backend requiere una categoría para registrar el producto.');
      return;
    }
    const data = {
      sku: form.sku.trim(),
      name: form.name.trim(),
      kind: form.kind || 'STOCKED_GOOD',
      categoryId: form.categoryId,
      costPrice: Number(form.costPrice),
      salePrice: Number(form.salePrice),
      description: form.description.trim()
    };
    if (form.attributes && typeof form.attributes === 'object' && !Array.isArray(form.attributes)) data.attributes = form.attributes;
    if (form.unitOfMeasureId) data.unitOfMeasureId = form.unitOfMeasureId;
    if (!Number.isFinite(data.costPrice) || data.costPrice < 0 || !Number.isFinite(data.salePrice) || data.salePrice < 0) {
      setError('El costo y el precio de venta deben ser números válidos no negativos.');
      return;
    }
    await runAction(() => editing ? api.updateProduct(pickId(editing), data) : api.createProduct(data), editing ? 'Producto actualizado.' : 'Producto creado.');
  }

  async function submitCategory(form) {
    const data = { name: form.name.trim(), description: form.description.trim() };
    await runAction(async () => {
      const result = editing ? await api.updateCategory(pickId(editing), data) : await api.createCategory(data);
      const categoryId = result?.id ?? result?.categoryId ?? (editing ? pickId(editing) : null);
      if (!categoryId) throw new Error('La API guardó la categoría pero no devolvió su identificador; no se pudo guardar el esquema de propiedades.');
      await api.saveCategoryAttributes(categoryId, { definitions: Array.isArray(form.definitions) ? form.definitions : [] });
    }, editing ? 'Categoría y propiedades actualizadas.' : 'Categoría creada con sus propiedades.');
  }

  function toggleProductStatus(product) {
    const inactive = product.active === false || String(product.status || '').toUpperCase() === 'INACTIVE';
    setStatusChangeTarget({ kind: 'product', item: product, activate: inactive });
    setModal('confirmStatusChange');
  }

  function toggleCategoryStatus(category) {
    const inactive = category.active === false || String(category.status || '').toUpperCase() === 'INACTIVE';
    setStatusChangeTarget({ kind: 'category', item: category, activate: inactive });
    setModal('confirmStatusChange');
  }

  async function confirmStatusChange(event) {
    event.preventDefault();
    if (!statusChangeTarget) return;
    const { kind, item, activate } = statusChangeTarget;
    const id = pickId(item);
    const action = kind === 'product'
      ? (activate ? () => api.activateProduct(id) : () => api.deactivateProduct(id))
      : (activate ? () => api.activateCategory(id) : () => api.deactivateCategory(id));
    const label = kind === 'product' ? 'producto' : 'categoría';
    const completed = await runAction(action, (activate ? 'Se activó el ' : 'Se desactivó el ') + label + '.');
    if (completed) setStatusChangeTarget(null);
  }

  async function logout() {
    try {
      await api.logout();
    } catch (logoutError) {
      setError('La sesión se cerró localmente, pero el backend no confirmó el cierre: ' + (logoutError?.message || 'error desconocido'));
    } finally {
      setToken('');
      setTokenState('');
      setUser(null);
    }
  }
  async function submitPurchase(data) {
    await runAction(() => api.createPurchaseOrder(data), 'Orden creada como borrador. Envíala desde la lista para solicitar la aprobación. El inventario cambia únicamente al contabilizar la recepción.');
  }

  function requestPurchaseCancellation(order) {
    setPurchaseToCancel(order);
    setError('');
    setModal('confirmPurchaseCancel');
  }

  async function confirmPurchaseCancellation(event) {
    event.preventDefault();
    if (!purchaseToCancel) return;
    const completed = await runAction(() => api.cancelPurchaseOrder(pickId(purchaseToCancel)), 'Orden cancelada. No se modificaron las existencias porque todavía no se había contabilizado la recepción.');
    if (completed) setPurchaseToCancel(null);
  }

  async function openReceive(order) {
    setLoading(true);
    setError('');
    try {
      const detail = await api.purchaseOrder(pickId(order));
      setSelectedOrder(detail);
      setModal('receive');
    } catch (err) {
      setError('No se pudo cargar el detalle de la orden para recibirla: ' + (err?.message || 'error'));
    } finally {
      setLoading(false);
    }
  }

  async function submitReceipt(data) {
    if (!selectedOrder) return;
    const completed = await runAction(() => api.createReceipt(pickId(selectedOrder), data), 'Recepción contabilizada. El inventario se actualizó desde el backend.');
    if (completed) setSelectedOrder(null);
  }

  async function submitSale(data) {
    await runAction(() => api.createSale(data), 'Venta registrada por el backend.');
  }

  function requestSaleCancellation(sale) {
    setSaleToCancel(sale);
    setSaleCancelReason('');
    setError('');
    setModal('cancelSale');
  }

  async function submitSaleCancellation(event) {
    event.preventDefault();
    if (!saleToCancel || !saleCancelReason.trim()) {
      setError('Debes indicar el motivo de anulación.');
      return;
    }
    const completed = await runAction(() => api.requestSaleCancellation(pickId(saleToCancel), { reason: saleCancelReason.trim() }), 'Solicitud de anulación enviada. Requiere aprobación de un usuario autorizado distinto.');
    if (completed) {
      setSaleToCancel(null);
      setSaleCancelReason('');
    }
  }

  async function approveSaleCancellation(sale) {
    await runAction(() => api.approveSaleCancellation(pickId(sale)), 'Anulación aprobada por el backend.');
  }

  async function loadInventoryFilter(event, filterValues = inventoryFilter) {
    event?.preventDefault?.();
    setLoading(true);
    setError('');
    const params = {};
    if (filterValues.warehouseId) params.warehouseId = filterValues.warehouseId;
    if (filterValues.productId) params.productId = filterValues.productId;
    params.size = '100';
    const [balanceResult, movementResult] = await Promise.allSettled([api.balances(params), api.movements(params)]);
    const unauthorized = [balanceResult, movementResult].some(result => result.status === 'rejected' && result.reason?.status === 401);
    if (unauthorized) {
      setToken(''); setTokenState(''); setUser(null); setApiStatus('error');
      setError('La sesión expiró o ya no es válida. Inicia sesión nuevamente.');
      setLoading(false);
      return;
    }
    const failures = [];
    if (balanceResult.status === 'fulfilled') setBalances(listFrom(balanceResult.value));
    else failures.push('saldos: ' + (balanceResult.reason?.message || 'error'));
    if (movementResult.status === 'fulfilled') setMovements(listFrom(movementResult.value));
    else failures.push('movimientos: ' + (movementResult.reason?.message || 'error'));
    if (failures.length) setError('No se pudieron aplicar todos los filtros. ' + failures.join(' · '));
    setLoading(false);
  }


  function openProduct(product = null) {
    setEditing(product);
    setModal('product');
  }
  function openCategory(category = null) {
    setEditing(category);
    setModal('category');
  }

  if (!token) return <Login onLogin={async (username, password) => {
    setError('');
    try { const result = await api.login(username, password); const access = result?.accessToken || result?.access_token || result?.token || result?.jwt; if (!access) throw new Error('La respuesta de login no contiene accessToken/access_token/token. Revisa el contrato de autenticación del backend.'); setToken(access); setTokenState(access); }
    catch (e) { setError(e.message); }
  }} error={error} />;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Boxes size={22}/></div><div><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div></div>
      <div className="workspace-label">ESPACIO DE TRABAJO</div>
      <nav>{nav.map(item => <button key={item.id} className={section === item.id ? 'nav-item active' : 'nav-item'} onClick={() => setSection(item.id)}><item.icon size={18}/><span>{item.label}</span>{section === item.id && <ChevronRight size={15} className="nav-chevron"/>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="api-status"><span className={'status-dot ' + apiStatus}/><span>{apiStatus === 'connected' ? 'API disponible' : apiStatus === 'checking' ? 'Conectando a la API…' : 'API sin conexión'}</span></div><button className="nav-item logout" onClick={logout}><LogOut size={18}/> Cerrar sesión</button></div>
    </aside>
    <main className="main-content">
      <header className="topbar"><div><span className="eyebrow">GESTIÓN COMERCIAL</span><h1>{nav.find(n => n.id === section)?.label || 'Resumen'}</h1></div><div className="topbar-actions"><button type="button" className="icon-button" onClick={loadData} title="Actualizar datos" aria-label="Actualizar datos"><RefreshCw size={17}/></button><div className="user-chip"><div className="avatar">{String(user?.name || user?.username || user?.email || 'U').slice(0,1).toUpperCase()}</div><div><strong>{user?.name || user?.username || user?.email || 'Usuario'}</strong><small>{(user?.roles || user?.authorities || ['Usuario']).map?.(r => r.name || r.authority || r) .join(', ') || 'Sesión activa'}</small></div></div></div></header>
      {error && <div className="alert error" role="alert" aria-live="assertive"><CircleAlert size={18}/><span>{error}</span><button type="button" aria-label="Cerrar mensaje de error" onClick={() => setError('')}>×</button></div>}
      {notice && <div className="alert success" role="status" aria-live="polite"><Activity size={18}/><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
      {section === 'overview' && <Overview products={products} categories={categories} stockTotal={stockTotal} inventoryValue={inventoryValueLabel} purchases={purchases} sales={sales} movements={movements} setSection={setSection} loading={loading} onNewProduct={() => openProduct()} onNewCategory={() => openCategory()} onNewPurchase={() => setModal('purchase')} onNewSale={() => setModal('sale')}/>}
      {section === 'products' && <section className="panel"><PanelHeading title="Catálogo de productos" subtitle="Administra productos, unidades y propiedades dinámicas." action="Nuevo producto" onAction={() => openProduct()} icon={PackagePlus}/><div className="toolbar"><div className="search-box"><Search size={17}/><input aria-label="Buscar productos por nombre o SKU" value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por nombre o SKU..."/></div><span className="muted">{visibleProducts.length} productos</span></div><ProductsTable products={visibleProducts} categories={categories} onEdit={openProduct} onToggleActive={toggleProductStatus}/></section>}
      {section === 'categories' && <section className="panel"><PanelHeading title="Categorías" subtitle="Organiza el catálogo y define la estructura de tus productos." action="Nueva categoría" onAction={() => openCategory()} icon={Tags}/><CategoriesTable categories={categories} onEdit={openCategory} onToggleActive={toggleCategoryStatus}/></section>}
      {section === 'partners' && <OperationsCatalogs onChanged={loadData}/>}
      {section === 'purchases' && <section className="panel"><PanelHeading title="Compras y entradas" subtitle="Órdenes con varias líneas, aprobación y recepción por almacén." action="Nueva compra" onAction={() => setModal('purchase')} icon={ArrowDownToLine}/><PurchaseTable rows={purchases} loading={loading} onSubmit={order => runAction(() => api.submitPurchaseOrder(pickId(order)), 'Orden enviada para aprobación.')} onApprove={order => runAction(() => api.approvePurchaseOrder(pickId(order)), 'Orden aprobada. Ya puedes registrar la recepción.')} onCancel={requestPurchaseCancellation} onReceive={openReceive}/><PurchaseReturns onChanged={loadData}/></section>}
      {section === 'sales' && <section className="panel"><PanelHeading title="Ventas y salidas" subtitle="Registra ventas de varios productos; el backend valida precios, stock y contabilización." action="Nueva venta" onAction={() => setModal('sale')} icon={ArrowUpFromLine}/><SalesTable rows={sales} loading={loading} onRequestCancel={requestSaleCancellation} onApproveCancellation={approveSaleCancellation}/><SalesReturns onChanged={loadData}/></section>}
      {section === 'inventory' && <section className="panel"><PanelHeading title="Existencias y movimientos" subtitle="Consulta los saldos y la trazabilidad filtrando por almacén y producto." action="Actualizar" onAction={loadData} icon={Warehouse}/><form className="report-filters inventory-filters" onSubmit={loadInventoryFilter}><Field label="Almacén"><select value={inventoryFilter.warehouseId} onChange={event => setInventoryFilter(current => ({...current,warehouseId:event.target.value}))}><option value="">Todos los almacenes</option>{warehouses.map(warehouse => <option key={pickId(warehouse)} value={pickId(warehouse)}>{pickName(warehouse)}</option>)}</select></Field><Field label="Producto"><select value={inventoryFilter.productId} onChange={event => setInventoryFilter(current => ({...current,productId:event.target.value}))}><option value="">Todos los productos</option>{products.map(product => <option key={pickId(product)} value={pickId(product)}>{product.name} · {product.sku}</option>)}</select></Field><button className="button primary" disabled={loading}><Search size={16}/> Aplicar filtros</button><button type="button" className="button secondary" disabled={loading} onClick={() => { const reset = {warehouseId:'',productId:''}; setInventoryFilter(reset); void loadInventoryFilter({preventDefault:()=>{}}, reset); }}>Limpiar</button></form><div className="stat-strip"><div><span>Unidades en saldo</span><strong>{stockTotal.toLocaleString('es-EC')}</strong></div><div><span>Valor estimado</span><strong>{inventoryValueLabel}</strong></div><div><span>Movimientos cargados</span><strong>{movements.length}</strong></div></div><h3 className="subheading">Saldos por producto</h3><DataTable rows={balances} columns={[['productName','Producto'],['sku','SKU'],['warehouseCode','Almacén'],['onHand','Existencia'],['reserved','Reservado'],['available','Disponible']]} empty="No hay saldos de inventario para mostrar."/><h3 className="subheading">Últimos movimientos</h3><DataTable rows={movements.slice(0, 30)} columns={[['type','Movimiento'],['productName','Producto'],['quantity','Cantidad'],['occurredAt','Fecha'],['referenceType','Tipo de documento'],['referenceId','Referencia']]} empty="No hay movimientos registrados."/><InventoryOperations products={products} warehouses={warehouses} suppliers={suppliers} onChanged={loadData}/></section>}
      {section === 'reports' && <ReportsCenter warehouses={warehouses} products={products} suppliers={suppliers} onUnauthorized={() => { setToken(''); setTokenState(''); setUser(null); }} />}
    </main>
    {modal && <Modal title={{product:editing ? 'Editar producto' : 'Crear producto',category:editing ? 'Editar categoría' : 'Crear categoría',purchase:'Registrar compra',receive:'Registrar recepción',sale:'Registrar venta',cancelSale:'Solicitar anulación de venta',confirmPurchaseCancel:'Confirmar cancelación de compra',confirmStatusChange:statusChangeTarget ? (statusChangeTarget.activate ? 'Confirmar activación' : 'Confirmar desactivación') : 'Confirmar cambio de estado'}[modal] || 'Operación'} onClose={() => { setModal(''); setEditing(null); if (modal === 'cancelSale') setSaleToCancel(null); if (modal === 'confirmPurchaseCancel') setPurchaseToCancel(null); if (modal === 'confirmStatusChange') setStatusChangeTarget(null); }}><>
      {modal === 'product' && <ProductForm key={'product-' + (pickId(editing) || 'new')} initialValue={editing} categories={categories} units={units} loading={loading} onCancel={() => { setModal(''); setEditing(null); }} onSubmit={submitProduct} onError={setError}/>}
      {modal === 'category' && <CategoryForm key={'category-' + (pickId(editing) || 'new')} initialValue={editing} loading={loading} onCancel={() => { setModal(''); setEditing(null); }} onSubmit={submitCategory} onError={setError}/>}
      {modal === 'purchase' && <PurchaseForm products={products} warehouses={warehouses} suppliers={suppliers} loading={loading} onCancel={() => setModal('')} onSubmit={submitPurchase}/>}
      {modal === 'receive' && selectedOrder && <ReceiptForm key={pickId(selectedOrder)} order={selectedOrder} loading={loading} onCancel={() => { setModal(''); setSelectedOrder(null); }} onSubmit={submitReceipt}/>}
      {modal === 'sale' && <SaleForm products={products} warehouses={warehouses} customers={customers} balances={balances} loading={loading} onCancel={() => setModal('')} onSubmit={submitSale}/>}
      {modal === 'confirmStatusChange' && statusChangeTarget && <form className="form-grid" onSubmit={confirmStatusChange}>
        <div className="alert warning wide" role="alert"><CircleAlert size={18}/><span>¿Confirmas que deseas {statusChangeTarget.activate ? 'activar' : 'desactivar'} el {statusChangeTarget.kind === 'product' ? 'producto' : 'categoría'} <strong>{statusChangeTarget.item.name || statusChangeTarget.item.sku || pickId(statusChangeTarget.item)}</strong>?</span></div>
        <p className="form-hint wide">{statusChangeTarget.activate ? 'El registro volverá a estar disponible según las reglas del backend.' : 'No se borran movimientos ni documentos históricos. Las operaciones futuras que requieran este registro podrán ser rechazadas.'}</p>
        <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => { setModal(''); setStatusChangeTarget(null); }}>Volver</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : statusChangeTarget.activate ? 'Confirmar activación' : 'Confirmar desactivación'}</button></div>
      </form>}
      {modal === 'confirmPurchaseCancel' && purchaseToCancel && <form className="form-grid" onSubmit={confirmPurchaseCancellation}><div className="alert warning wide" role="alert"><CircleAlert size={18}/><span>Vas a cancelar la orden <strong>{purchaseToCancel.orderNumber || purchaseToCancel.number || pickId(purchaseToCancel)}</strong>. Esta acción no se puede deshacer desde el formulario y no aumenta ni reduce existencias.</span></div><p className="form-hint wide">Proveedor: {purchaseToCancel.supplierName || purchaseToCancel.supplier?.name || '—'} · Almacén: {purchaseToCancel.warehouseCode || '—'}.</p><div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => { setModal(''); setPurchaseToCancel(null); }}>Volver</button><button className="button danger" disabled={loading}>{loading ? 'Cancelando…' : 'Confirmar cancelación'}</button></div></form>}
      {modal === 'cancelSale' && saleToCancel && <form className="form-grid" onSubmit={submitSaleCancellation}><p className="form-hint wide">Venta: <strong>{saleToCancel.number || saleToCancel.code || pickId(saleToCancel)}</strong>. La anulación no devuelve las existencias hasta que el backend la apruebe.</p><Field label="Motivo de anulación" required wide><textarea rows="3" required maxLength="500" value={saleCancelReason} onChange={event => setSaleCancelReason(event.target.value)} placeholder="Describe el motivo de la solicitud"/></Field><FormActions loading={loading} onCancel={() => { setModal(''); setSaleToCancel(null); }}/></form>}

    </></Modal>}
  </div>;
}

function Login({ onLogin, error }) {
  const [username,setUsername] = useState(''); const [password,setPassword] = useState(''); const [busy,setBusy] = useState(false);
  return <div className="login-screen"><div className="login-card"><div className="brand login-brand"><div className="brand-mark"><Boxes size={23}/></div><div><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div></div><span className="eyebrow">BIENVENIDO DE NUEVO</span><h1>Controla tu inventario con claridad.</h1><p>Ingresa con tus credenciales para administrar productos, compras y ventas.</p>{error && <div className="alert error" role="alert" aria-live="assertive">{error}</div>}<form onSubmit={async e=>{e.preventDefault();setBusy(true);await onLogin(username,password);setBusy(false);}}><Field label="Usuario o correo"><input autoComplete="username" required value={username} onChange={e=>setUsername(e.target.value)} placeholder="Tu usuario"/></Field><Field label="Contraseña"><input type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} placeholder="Tu contraseña"/></Field><button className="button primary full" disabled={busy}>{busy?'Conectando…':'Iniciar sesión'}<ChevronRight size={17}/></button></form><small className="login-foot">Conexión segura al backend Inventary</small></div></div>;
}
function PanelHeading({title,subtitle,action,onAction,icon:Icon=Layers3}) { return <div className="panel-heading"><div className="panel-title-icon"><span><Icon size={20}/></span><div><h2>{title}</h2><p>{subtitle}</p></div></div>{action&&<button type="button" className="button primary" onClick={onAction}><Plus size={16}/>{action}</button>}</div>; }
function Field({label,required,wide,children}) { return <label className={'field'+(wide?' wide':'')}><span>{label}{required&&<b> *</b>}</span>{children}</label>; }
function FormActions({loading,onCancel}) { return <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading}>{loading?'Guardando…':'Guardar cambios'}</button></div>; }
function Modal({title,onClose,children}) {
  const dialogRef = useAccessibleDialog(true, onClose);
  return <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="global-modal-title" tabIndex={-1}>
      <div className="modal-heading"><h2 id="global-modal-title">{title}</h2><button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar ventana">×</button></div>
      {children}
    </section>
  </div>;
}

export default App;

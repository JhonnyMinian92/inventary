import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, ArrowDownToLine, ArrowUpFromLine, Boxes, ChartNoAxesCombined, ChevronRight, CircleAlert, ClipboardList, Layers3, LogOut, PackagePlus, Plus, RefreshCw, Search, ShoppingBag, Tags, Warehouse } from 'lucide-react';
import { api, getToken, listFrom, setToken } from './api';
import ProductForm from './features/catalog/ProductForm.jsx';
import CategoryForm from './features/catalog/CategoryForm.jsx';
import PurchaseForm from './features/purchasing/PurchaseForm.jsx';
import ReceiptForm from './features/purchasing/ReceiptForm.jsx';
import SaleForm from './features/sales/SaleForm.jsx';
import OperationsCatalogs from './features/operations/OperationsCatalogs.jsx';
import InventoryOperations from './features/inventory/InventoryOperations.jsx';

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
  const [report, setReport] = useState([]);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [apiStatus, setApiStatus] = useState('checking');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState('');
  const [editing, setEditing] = useState(null);
  const [inventoryFilter, setInventoryFilter] = useState({ warehouseId: '', productId: '' });
  const [reportForm, setReportForm] = useState({ warehouseId: '', from: new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10), to: new Date().toISOString().slice(0, 10), productId: '' });

  const loadData = useCallback(async () => {
    setLoading(true); setError('');
    const tasks = [
      ['products', api.products()], ['categories', api.categories()], ['units', api.units()],
      ['warehouses', api.warehouses()], ['suppliers', api.suppliers()], ['customers', api.customers()], ['balances', api.balances()], ['movements', api.movements()],
      ['purchases', api.purchaseOrders()], ['sales', api.sales()]
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
  const inventoryValue = balances.reduce((sum, row) => sum + Number(quantity(row)) * Number(row.averageCost ?? row.unitCost ?? row.cost ?? 0), 0);

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
    const data = {
      sku: form.sku.trim(),
      name: form.name.trim(),
      categoryId: form.categoryId || null,
      costPrice: Number(form.costPrice),
      salePrice: Number(form.salePrice),
      description: form.description.trim(),
      attributes: form.attributes && typeof form.attributes === 'object' && !Array.isArray(form.attributes) ? form.attributes : {}
    };
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

  async function toggleProductStatus(product) {
    const inactive = product.active === false || String(product.status || '').toUpperCase() === 'INACTIVE';
    const id = pickId(product);
    await runAction(() => inactive ? api.activateProduct(id) : api.deactivateProduct(id), inactive ? 'Producto activado.' : 'Producto desactivado.');
  }

  async function toggleCategoryStatus(category) {
    const inactive = category.active === false || String(category.status || '').toUpperCase() === 'INACTIVE';
    const id = pickId(category);
    await runAction(() => inactive ? api.activateCategory(id) : api.deactivateCategory(id), inactive ? 'Categoría activada.' : 'Categoría desactivada.');
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
    await runAction(async () => {
      const order = await api.createPurchaseOrder(data);
      const orderId = order?.id ?? order?.purchaseOrderId ?? order?.orderId;
      if (!orderId) throw new Error('La API creó la orden pero no devolvió su identificador.');
      await api.submitPurchaseOrder(orderId);
    }, 'Orden creada y enviada para aprobación. La recepción requiere aprobación del backend.');
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

  async function requestSaleCancellation(sale) {
    const reason = window.prompt('Indica el motivo para solicitar la anulación de esta venta:');
    if (!reason?.trim()) return;
    await runAction(() => api.requestSaleCancellation(pickId(sale), { reason: reason.trim() }), 'Solicitud de anulación enviada. Requiere aprobación autorizada.');
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
    const failures = [];
    if (balanceResult.status === 'fulfilled') setBalances(listFrom(balanceResult.value));
    else failures.push('saldos: ' + (balanceResult.reason?.message || 'error'));
    if (movementResult.status === 'fulfilled') setMovements(listFrom(movementResult.value));
    else failures.push('movimientos: ' + (movementResult.reason?.message || 'error'));
    if (failures.length) setError('No se pudieron aplicar todos los filtros. ' + failures.join(' · '));
    setLoading(false);
  }

  async function runReport(e) {
    e.preventDefault();
    await runAction(async () => {
      const params = { warehouseId: reportForm.warehouseId, from: reportForm.from, to: reportForm.to };
      if (reportForm.productId) params.productId = reportForm.productId;
      const result = await api.inventoryBalanceReport(params);
      setReport(listFrom(result));
    }, 'Reporte actualizado.');
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
      <header className="topbar"><div><span className="eyebrow">GESTIÓN COMERCIAL</span><h1>{nav.find(n => n.id === section)?.label || 'Resumen'}</h1></div><div className="topbar-actions"><button className="icon-button" onClick={loadData} title="Actualizar datos"><RefreshCw size={17}/></button><div className="user-chip"><div className="avatar">{String(user?.name || user?.username || user?.email || 'U').slice(0,1).toUpperCase()}</div><div><strong>{user?.name || user?.username || user?.email || 'Usuario'}</strong><small>{(user?.roles || user?.authorities || ['Usuario']).map?.(r => r.name || r.authority || r) .join(', ') || 'Sesión activa'}</small></div></div></div></header>
      {error && <div className="alert error"><CircleAlert size={18}/><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
      {notice && <div className="alert success"><Activity size={18}/><span>{notice}</span><button onClick={() => setNotice('')}>×</button></div>}
      {section === 'overview' && <Overview products={products} categories={categories} stockTotal={stockTotal} inventoryValue={inventoryValue} purchases={purchases} sales={sales} movements={movements} setSection={setSection} loading={loading}/>}
      {section === 'products' && <section className="panel"><PanelHeading title="Catálogo de productos" subtitle="Administra productos, unidades y propiedades dinámicas." action="Nuevo producto" onAction={() => openProduct()} icon={PackagePlus}/><div className="toolbar"><div className="search-box"><Search size={17}/><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por nombre o SKU..."/></div><span className="muted">{visibleProducts.length} productos</span></div><ProductsTable products={visibleProducts} categories={categories} onEdit={openProduct} onToggleActive={toggleProductStatus}/></section>}
      {section === 'categories' && <section className="panel"><PanelHeading title="Categorías" subtitle="Organiza el catálogo y define la estructura de tus productos." action="Nueva categoría" onAction={() => openCategory()} icon={Tags}/><CategoriesTable categories={categories} onEdit={openCategory} onToggleActive={toggleCategoryStatus}/></section>}
      {section === 'partners' && <OperationsCatalogs onChanged={loadData}/>}
      {section === 'purchases' && <section className="panel"><PanelHeading title="Compras y entradas" subtitle="Órdenes con varias líneas, aprobación y recepción por almacén." action="Nueva compra" onAction={() => setModal('purchase')} icon={ArrowDownToLine}/><PurchaseTable rows={purchases} loading={loading} onSubmit={order => runAction(() => api.submitPurchaseOrder(pickId(order)), 'Orden enviada para aprobación.')} onApprove={order => runAction(() => api.approvePurchaseOrder(pickId(order)), 'Orden aprobada. Ya puedes registrar la recepción.')} onCancel={order => runAction(() => api.cancelPurchaseOrder(pickId(order)), 'Orden cancelada.')} onReceive={openReceive}/></section>}
      {section === 'sales' && <section className="panel"><PanelHeading title="Ventas y salidas" subtitle="Registra ventas de varios productos; el backend valida precios, stock y contabilización." action="Nueva venta" onAction={() => setModal('sale')} icon={ArrowUpFromLine}/><SalesTable rows={sales} loading={loading} onRequestCancel={requestSaleCancellation} onApproveCancellation={approveSaleCancellation}/></section>}
      {section === 'inventory' && <section className="panel"><PanelHeading title="Existencias y movimientos" subtitle="Consulta los saldos y la trazabilidad filtrando por almacén y producto." action="Actualizar" onAction={loadData} icon={Warehouse}/><form className="report-filters inventory-filters" onSubmit={loadInventoryFilter}><Field label="Almacén"><select value={inventoryFilter.warehouseId} onChange={event => setInventoryFilter(current => ({...current,warehouseId:event.target.value}))}><option value="">Todos los almacenes</option>{warehouses.map(warehouse => <option key={pickId(warehouse)} value={pickId(warehouse)}>{pickName(warehouse)}</option>)}</select></Field><Field label="Producto"><select value={inventoryFilter.productId} onChange={event => setInventoryFilter(current => ({...current,productId:event.target.value}))}><option value="">Todos los productos</option>{products.map(product => <option key={pickId(product)} value={pickId(product)}>{product.name} · {product.sku}</option>)}</select></Field><button className="button primary" disabled={loading}><Search size={16}/> Aplicar filtros</button><button type="button" className="button secondary" disabled={loading} onClick={() => { const reset = {warehouseId:'',productId:''}; setInventoryFilter(reset); void loadInventoryFilter({preventDefault:()=>{}}, reset); }}>Limpiar</button></form><div className="stat-strip"><div><span>Unidades en saldo</span><strong>{stockTotal.toLocaleString('es-EC')}</strong></div><div><span>Valor estimado</span><strong>{money(inventoryValue)}</strong></div><div><span>Movimientos cargados</span><strong>{movements.length}</strong></div></div><h3 className="subheading">Saldos por producto</h3><DataTable rows={balances} columns={[['productName','Producto'],['sku','SKU'],['warehouseName','Almacén'],['onHand','Existencia'],['reserved','Reservado'],['available','Disponible']]} empty="No hay saldos de inventario para mostrar."/><h3 className="subheading">Últimos movimientos</h3><DataTable rows={movements.slice(0, 30)} columns={[['movementType','Movimiento'],['productName','Producto'],['quantity','Cantidad'],['createdAt','Fecha'],['reference','Documento']]} empty="No hay movimientos registrados."/><InventoryOperations products={products} warehouses={warehouses} suppliers={suppliers} onChanged={loadData}/></section>}
      {section === 'reports' && <section className="panel"><PanelHeading title="Balance de inventario" subtitle="Compara compras, ventas y saldo por rango de fechas." icon={ClipboardList}/><form className="report-filters" onSubmit={runReport}><Field label="Almacén" required><select required value={reportForm.warehouseId} onChange={e => setReportForm({...reportForm, warehouseId:e.target.value})}><option value="">Seleccionar almacén</option>{warehouses.map(w => <option key={pickId(w)} value={pickId(w)}>{pickName(w)}</option>)}</select></Field><Field label="Desde" required><input type="date" required value={reportForm.from} onChange={e => setReportForm({...reportForm, from:e.target.value})}/></Field><Field label="Hasta" required><input type="date" min={reportForm.from} required value={reportForm.to} onChange={e => setReportForm({...reportForm, to:e.target.value})}/></Field><Field label="Producto (opcional)"><select value={reportForm.productId} onChange={e => setReportForm({...reportForm, productId:e.target.value})}><option value="">Todos los productos</option>{products.map(p => <option key={pickId(p)} value={pickId(p)}>{p.name} · {p.sku}</option>)}</select></Field><button className="button primary" disabled={loading}><Search size={16}/> Consultar balance</button></form><DataTable rows={report} columns={[['productName','Producto'],['sku','SKU'],['openingStock','Saldo inicial'],['purchasedQuantity','Comprado'],['purchaseAmount','Importe compras'],['soldQuantity','Vendido'],['salesAmount','Importe ventas'],['closingStock','Saldo final'],['currentStock','Stock actual'],['grossProfit','Ganancia bruta']]} empty="Selecciona un almacén y consulta un periodo para ver el balance."/></section>}
    </main>
    {modal && <Modal title={{product:editing ? 'Editar producto' : 'Crear producto',category:editing ? 'Editar categoría' : 'Crear categoría',purchase:'Registrar compra',receive:'Registrar recepción',sale:'Registrar venta'}[modal] || 'Operación'} onClose={() => { setModal(''); setEditing(null); }}><>
      {modal === 'product' && <ProductForm key={'product-' + (pickId(editing) || 'new')} initialValue={editing} categories={categories} units={units} loading={loading} onCancel={() => { setModal(''); setEditing(null); }} onSubmit={submitProduct} onError={setError}/>}
      {modal === 'category' && <CategoryForm key={'category-' + (pickId(editing) || 'new')} initialValue={editing} loading={loading} onCancel={() => { setModal(''); setEditing(null); }} onSubmit={submitCategory} onError={setError}/>}
      {modal === 'purchase' && <PurchaseForm products={products} warehouses={warehouses} suppliers={suppliers} loading={loading} onCancel={() => setModal('')} onSubmit={submitPurchase}/>}
      {modal === 'receive' && selectedOrder && <ReceiptForm key={pickId(selectedOrder)} order={selectedOrder} loading={loading} onCancel={() => { setModal(''); setSelectedOrder(null); }} onSubmit={submitReceipt}/>}
      {modal === 'sale' && <SaleForm products={products} warehouses={warehouses} customers={customers} balances={balances} loading={loading} onCancel={() => setModal('')} onSubmit={submitSale}/>}

    </></Modal>}
  </div>;
}

function Login({ onLogin, error }) {
  const [username,setUsername] = useState(''); const [password,setPassword] = useState(''); const [busy,setBusy] = useState(false);
  return <div className="login-screen"><div className="login-card"><div className="brand login-brand"><div className="brand-mark"><Boxes size={23}/></div><div><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div></div><span className="eyebrow">BIENVENIDO DE NUEVO</span><h1>Controla tu inventario con claridad.</h1><p>Ingresa con tus credenciales para administrar productos, compras y ventas.</p>{error && <div className="alert error">{error}</div>}<form onSubmit={async e=>{e.preventDefault();setBusy(true);await onLogin(username,password);setBusy(false);}}><Field label="Usuario o correo"><input autoComplete="username" required value={username} onChange={e=>setUsername(e.target.value)} placeholder="Tu usuario"/></Field><Field label="Contraseña"><input type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)} placeholder="Tu contraseña"/></Field><button className="button primary full" disabled={busy}>{busy?'Conectando…':'Iniciar sesión'}<ChevronRight size={17}/></button></form><small className="login-foot">Conexión segura al backend Inventary</small></div></div>;
}
function Overview({products,categories,stockTotal,inventoryValue,purchases,sales,movements,setSection,loading}) {
  const cards = [{label:'Productos',value:products.length,detail:'Referencias registradas',icon:Boxes,color:'violet'},{label:'Categorías',value:categories.length,detail:'Grupos de catálogo',icon:Tags,color:'blue'},{label:'Unidades en stock',value:stockTotal.toLocaleString('es-EC'),detail:'Suma de saldos disponibles',icon:Warehouse,color:'green'},{label:'Valor estimado',value:money(inventoryValue),detail:'Según costo reportado por API',icon:ChartNoAxesCombined,color:'orange'}];
  return <><div className="welcome-row"><div><h2>Todo tu inventario, en un solo lugar.</h2><p>Consulta el estado del negocio y accede rápidamente a las operaciones principales.</p></div><button className="button secondary" onClick={()=>setSection('reports')}><ChartNoAxesCombined size={16}/> Ver balance</button></div><div className="stats-grid">{cards.map(c=><div className="stat-card" key={c.label}><div className="stat-top"><span>{c.label}</span><span className={'stat-icon '+c.color}><c.icon size={19}/></span></div><strong>{c.value}</strong><small>{c.detail}</small></div>)}</div><div className="content-grid"><section className="panel quick-panel"><div className="section-heading"><div><h3>Acciones rápidas</h3><p>Continúa con una tarea frecuente.</p></div></div><div className="quick-actions"><QuickAction icon={PackagePlus} title="Crear producto" text="Añade una referencia al catálogo" onClick={()=>setSection('products')}/><QuickAction icon={Tags} title="Nueva categoría" text="Organiza tus productos" onClick={()=>setSection('categories')}/><QuickAction icon={ArrowDownToLine} title="Registrar compra" text="Abastece tu inventario" onClick={()=>setSection('purchases')}/><QuickAction icon={ArrowUpFromLine} title="Registrar venta" text="Registra una salida comercial" onClick={()=>setSection('sales')}/></div></section><section className="panel activity-panel"><div className="section-heading"><div><h3>Actividad reciente</h3><p>Últimos movimientos registrados</p></div><button className="text-button" onClick={()=>setSection('inventory')}>Ver todo <ChevronRight size={14}/></button></div>{loading?<div className="empty-state">Actualizando datos…</div>:movements.length===0?<div className="empty-state"><Activity size={24}/><span>Aún no hay actividad para mostrar.</span></div>:movements.slice(0,5).map((m,i)=><div className="activity-row" key={m.id||i}><div className="activity-icon"><Activity size={16}/></div><div className="activity-copy"><strong>{titleCase(m.movementType||m.type||'Movimiento de inventario')}</strong><small>{m.productName||m.product?.name||m.reference||'Movimiento registrado'}</small></div><div className="activity-meta"><strong>{m.quantity??m.delta??'—'}</strong><small>{date(m.createdAt||m.occurredAt)}</small></div></div>)}</section></div><div className="bottom-note"><span className="status-dot"/><span>Los saldos y documentos provienen directamente de la API.</span><span className="note-separator">·</span><span>{purchases.length} compras</span><span className="note-separator">·</span><span>{sales.length} ventas</span></div></>;
}
function QuickAction({icon:Icon,title,text,onClick}) { return <button className="quick-action" onClick={onClick}><span className="quick-icon"><Icon size={20}/></span><span><strong>{title}</strong><small>{text}</small></span><ChevronRight size={16}/></button>; }
function PanelHeading({title,subtitle,action,onAction,icon:Icon=Layers3}) { return <div className="panel-heading"><div className="panel-title-icon"><span><Icon size={20}/></span><div><h2>{title}</h2><p>{subtitle}</p></div></div>{action&&<button className="button primary" onClick={onAction}><Plus size={16}/>{action}</button>}</div>; }
function ProductsTable({products,categories,onEdit,onToggleActive}) {
  const categoryName = id => categories.find(c => String(pickId(c)) === String(id))?.name || '—';
  return <div className="table-wrap"><table><thead><tr><th>Producto</th><th>SKU</th><th>Categoría</th><th>Tipo</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{products.map((product,index)=>{
    const inactive = product.active === false || String(product.status || '').toUpperCase() === 'INACTIVE';
    return <tr key={pickId(product)||index}><td><div className="product-cell"><span className="product-avatar"><ShoppingBag size={17}/></span><div><strong>{product.name||'Sin nombre'}</strong><small>{product.description||'Sin descripción'}</small></div></div></td><td><code>{product.sku||product.code||'—'}</code></td><td>{product.category?.name||categoryName(product.categoryId)}</td><td>{titleCase(product.kind||'PRODUCT')}</td><td><span className={'pill '+(inactive?'inactive':'active')}>{inactive?'Inactivo':'Activo'}</span></td><td><div className="row-actions"><button className="small-action" onClick={()=>onEdit(product)}>Editar</button><button className="small-action" onClick={()=>onToggleActive(product)}>{inactive?'Activar':'Desactivar'}</button></div></td></tr>;
  })}</tbody></table>{!products.length&&<div className="empty-state">No hay productos que coincidan con la búsqueda.</div>}</div>;
}
function CategoriesTable({categories,onEdit,onToggleActive}) {
  return <div className="table-wrap"><table><thead><tr><th>Categoría</th><th>Código</th><th>Descripción</th><th>Productos</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{categories.map((category,index)=>{
    const inactive = category.active === false || String(category.status || '').toUpperCase() === 'INACTIVE';
    return <tr key={pickId(category)||index}><td><strong>{category.name||'Sin nombre'}</strong></td><td><code>{category.code||'—'}</code></td><td>{category.description||'—'}</td><td>{category.productCount??'—'}</td><td><span className={'pill '+(inactive?'inactive':'active')}>{inactive?'Inactiva':'Activa'}</span></td><td><div className="row-actions"><button className="small-action" onClick={()=>onEdit(category)}>Editar</button><button className="small-action" onClick={()=>onToggleActive(category)}>{inactive?'Activar':'Desactivar'}</button></div></td></tr>;
  })}</tbody></table>{!categories.length&&<div className="empty-state">Todavía no has creado categorías.</div>}</div>;
}
function PurchaseTable({rows,onSubmit,onApprove,onCancel,onReceive,loading}) {
  return <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Estado</th><th>Proveedor</th><th>Almacén</th><th>Líneas</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>{rows.map((order,index)=>{
    const status=String(order.status||'DRAFT').toUpperCase();
    const items=Array.isArray(order.items)?order.items:Array.isArray(order.lines)?order.lines:[];
    const canSubmit=['DRAFT'].includes(status);
    const canApprove=['SUBMITTED','PENDING_APPROVAL','PENDING'].includes(status);
    const canReceive=['APPROVED','PARTIALLY_RECEIVED'].includes(status);
    const canCancel=['DRAFT','SUBMITTED','PENDING_APPROVAL','PENDING'].includes(status);
    const tone=['RECEIVED','COMPLETED'].includes(status)?'active':['CANCELLED','REJECTED'].includes(status)?'inactive':'pending';
    return <tr key={pickId(order)||index}>
      <td><strong>{order.number||order.code||String(pickId(order)).slice(0,12)||'Orden'}</strong></td>
      <td><span className={'pill '+tone}>{titleCase(status)}</span></td>
      <td>{order.supplierName||order.supplier?.name||'—'}</td>
      <td>{order.warehouseName||order.warehouse?.name||'—'}</td>
      <td>{items.length || order.itemCount || '—'}</td>
      <td>{date(order.createdAt)}</td>
      <td><div className="row-actions">
        {canSubmit&&<button className="small-action" disabled={loading} onClick={()=>onSubmit(order)}>Enviar</button>}
        {canApprove&&<button className="small-action" disabled={loading} onClick={()=>onApprove(order)}>Aprobar</button>}
        {canReceive&&<button className="small-action" disabled={loading} onClick={()=>onReceive(order)}>Recibir</button>}
        {canCancel&&<button className="small-action" disabled={loading} onClick={()=>onCancel(order)}>Cancelar</button>}
      </div></td>
    </tr>;
  })}</tbody></table>{!rows.length&&<div className="empty-state">Todavía no hay órdenes de compra.</div>}</div>;
}
function SalesTable({rows,loading,onRequestCancel,onApproveCancellation}) {
  return <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Estado</th><th>Cliente</th><th>Almacén</th><th>Fecha</th><th>Total</th><th>Acciones</th></tr></thead><tbody>
    {rows.map((sale,index)=>{
      const status=String(sale.status||'COMPLETED').toUpperCase();
      const canRequest=['COMPLETED','POSTED'].includes(status);
      const canApprove=status.includes('CANCEL') && !['CANCELLED','CANCELED'].includes(status);
      return <tr key={pickId(sale)||index}>
        <td><strong>{sale.number||sale.code||String(pickId(sale)).slice(0,12)||'Venta'}</strong></td>
        <td><span className={'pill '+(['COMPLETED','POSTED'].includes(status)?'active':['CANCELLED','CANCELED'].includes(status)?'inactive':'pending')}>{titleCase(status)}</span></td>
        <td>{sale.customerName||sale.customer?.name||'Venta de mostrador'}</td>
        <td>{sale.warehouseName||sale.warehouse?.name||'—'}</td>
        <td>{date(sale.createdAt)}</td>
        <td>{money(sale.total)}</td>
        <td><div className="row-actions">
          {canRequest&&<button className="small-action" disabled={loading} onClick={()=>onRequestCancel(sale)}>Solicitar anulación</button>}
          {canApprove&&<button className="small-action" disabled={loading} onClick={()=>onApproveCancellation(sale)}>Aprobar anulación</button>}
        </div></td>
      </tr>;
    })}
  </tbody></table>{!rows.length&&<div className="empty-state">Todavía no hay ventas registradas.</div>}</div>;
}
function DataTable({rows,columns,moneyKey,empty}) { return <div className="table-wrap"><table><thead><tr>{columns.map(([key,label])=><th key={key}>{label}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={pickId(row)||row.id||i}>{columns.map(([key])=><td key={key}>{key===moneyKey?money(row[key]):key.toLowerCase().includes('date')||key==='createdAt'?date(row[key]):key==='status'||key==='movementType'?titleCase(row[key]||'—'):row[key]??row.productName??row.warehouseName??row.supplierName??row.customer?.name??row.customerName??row.product?.name??row.warehouse?.name??'—'}</td>)}</tr>)}</tbody></table>{!rows.length&&<div className="empty-state">{empty}</div>}</div>; }
function Field({label,required,wide,children}) { return <label className={'field'+(wide?' wide':'')}><span>{label}{required&&<b> *</b>}</span>{children}</label>; }
function FormActions({loading,onCancel}) { return <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading}>{loading?'Guardando…':'Guardar cambios'}</button></div>; }
function Modal({title,onClose,children}) { return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onClose();}}><section className="modal-card"><div className="modal-heading"><h2>{title}</h2><button className="icon-button" onClick={onClose} aria-label="Cerrar">×</button></div>{children}</section></div>; }

export default App;

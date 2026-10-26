import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, ArrowDownToLine, ArrowUpFromLine, Boxes, ChartNoAxesCombined, ChevronRight, CircleAlert, ClipboardList, Layers3, LogOut, PackagePlus, Plus, RefreshCw, Search, ShoppingBag, Tags, Warehouse } from 'lucide-react';
import { api, getToken, listFrom, setToken } from './api';

const nav = [
  { id: 'overview', label: 'Resumen', icon: ChartNoAxesCombined },
  { id: 'products', label: 'Productos', icon: Boxes },
  { id: 'categories', label: 'Categorías', icon: Tags },
  { id: 'purchases', label: 'Compras', icon: ArrowDownToLine },
  { id: 'sales', label: 'Ventas', icon: ArrowUpFromLine },
  { id: 'inventory', label: 'Inventario', icon: Warehouse },
  { id: 'reports', label: 'Reportes', icon: ClipboardList }
];
const emptyProduct = { sku: '', name: '', kind: 'PRODUCT', categoryId: '', unitOfMeasureId: '', description: '', attributes: '{}' };
const emptyCategory = { code: '', name: '', description: '' };
const emptyPurchase = { supplierId: '', warehouseId: '', productId: '', quantity: '1', unitCost: '0', notes: '' };
const emptySale = { warehouseId: '', productId: '', quantity: '1', unitPrice: '0', notes: '' };

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
  const [balances, setBalances] = useState([]);
  const [movements, setMovements] = useState([]);
  const [purchases, setPurchases] = useState([]);
  const [sales, setSales] = useState([]);
  const [report, setReport] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState('');
  const [editing, setEditing] = useState(null);
  const [productForm, setProductForm] = useState(emptyProduct);
  const [categoryForm, setCategoryForm] = useState(emptyCategory);
  const [purchaseForm, setPurchaseForm] = useState(emptyPurchase);
  const [saleForm, setSaleForm] = useState(emptySale);
  const [reportForm, setReportForm] = useState({ warehouseId: '', from: new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10), to: new Date().toISOString().slice(0, 10), productId: '' });

  const loadData = useCallback(async () => {
    setLoading(true); setError('');
    const tasks = [
      ['products', api.products()], ['categories', api.categories()], ['units', api.units()],
      ['warehouses', api.warehouses()], ['balances', api.balances()], ['movements', api.movements()],
      ['purchases', api.purchaseOrders()], ['sales', api.sales()]
    ];
    const results = await Promise.allSettled(tasks.map(t => t[1]));
    const failed = [];
    results.forEach((result, i) => {
      const [key] = tasks[i];
      if (result.status === 'fulfilled') {
        const value = listFrom(result.value);
        ({ products: setProducts, categories: setCategories, units: setUnits, warehouses: setWarehouses, balances: setBalances, movements: setMovements, purchases: setPurchases, sales: setSales })[key](value);
      } else if (['products', 'categories', 'warehouses', 'balances'].includes(key)) failed.push(`${key}: ${result.reason?.message || 'error'}`);
    });
    if (failed.length) setError(`No se pudieron cargar algunos datos. ${failed.join(' · ')}`);
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!token) return;
    api.me().then(setUser).catch(e => { if (e.status === 401) { setToken(''); setTokenState(''); } else setError(e.message); });
    loadData();
  }, [token, loadData]);

  const visibleProducts = useMemo(() => products.filter(p => [p.name, p.sku, p.code, p.description].some(v => String(v || '').toLowerCase().includes(search.toLowerCase()))), [products, search]);
  const stockTotal = balances.reduce((sum, row) => sum + Number(quantity(row)), 0);
  const inventoryValue = balances.reduce((sum, row) => sum + Number(quantity(row)) * Number(row.averageCost ?? row.unitCost ?? row.cost ?? 0), 0);

  async function runAction(action, success) {
    setLoading(true); setError(''); setNotice('');
    try { await action(); setNotice(success); setModal(''); setEditing(null); await loadData(); }
    catch (e) { setError(e.message || 'No se pudo completar la operación.'); }
    finally { setLoading(false); }
  }

  async function submitProduct(e) {
    e.preventDefault();
    let attributes;
    try { attributes = JSON.parse(productForm.attributes || '{}'); } catch { setError('Las propiedades deben ser un JSON válido.'); return; }
    const data = { sku: productForm.sku.trim(), name: productForm.name.trim(), kind: productForm.kind, categoryId: productForm.categoryId || null, unitOfMeasureId: productForm.unitOfMeasureId || null, description: productForm.description.trim(), attributes };
    await runAction(() => editing ? api.updateProduct(pickId(editing), data) : api.createProduct(data), editing ? 'Producto actualizado.' : 'Producto creado.');
  }
  async function submitCategory(e) {
    e.preventDefault();
    const data = { code: categoryForm.code.trim(), name: categoryForm.name.trim(), description: categoryForm.description.trim() };
    await runAction(() => editing ? api.updateCategory(pickId(editing), data) : api.createCategory(data), editing ? 'Categoría actualizada.' : 'Categoría creada.');
  }
  async function submitPurchase(e) {
    e.preventDefault();
    const data = { warehouseId: purchaseForm.warehouseId, supplierId: purchaseForm.supplierId || null, notes: purchaseForm.notes, lines: [{ productId: purchaseForm.productId, quantity: Number(purchaseForm.quantity), unitCost: Number(purchaseForm.unitCost) }] };
    await runAction(async () => {
      const order = await api.createPurchaseOrder(data);
      const orderId = order?.id ?? order?.purchaseOrderId ?? order?.orderId;
      if (!orderId) throw new Error('La orden se creó, pero la API no devolvió su identificador; no se pudo registrar la recepción automáticamente. Revisa Compras y completa la recepción desde el documento.');
      await api.createReceipt(orderId, { warehouseId: purchaseForm.warehouseId, notes: purchaseForm.notes, lines: [{ productId: purchaseForm.productId, quantityReceived: Number(purchaseForm.quantity), unitCost: Number(purchaseForm.unitCost) }] });
    }, 'Compra recibida y contabilizada. Se actualizaron los saldos desde el backend.');
  }
  async function submitSale(e) {
    e.preventDefault();
    const data = { warehouseId: saleForm.warehouseId, notes: saleForm.notes, lines: [{ productId: saleForm.productId, quantity: Number(saleForm.quantity), unitPrice: Number(saleForm.unitPrice) }] };
    await runAction(() => api.createSale(data), 'Venta registrada.');
  }
  async function runReport(e) {
    e.preventDefault();
    await runAction(async () => {
      const params = { warehouseId: reportForm.warehouseId, from: new Date(reportForm.from + 'T00:00:00Z').toISOString(), to: new Date(reportForm.to + 'T23:59:59.999Z').toISOString() };
      if (reportForm.productId) params.productId = reportForm.productId;
      const result = await api.inventoryBalanceReport(params);
      setReport(listFrom(result));
    }, 'Reporte actualizado.');
  }
  function openProduct(product = null) {
    setEditing(product); setProductForm(product ? { sku: product.sku || '', name: product.name || '', kind: product.kind || 'PRODUCT', categoryId: product.categoryId || product.category?.id || '', unitOfMeasureId: product.unitOfMeasureId || product.unitOfMeasure?.id || '', description: product.description || '', attributes: JSON.stringify(product.attributes || {}, null, 2) } : emptyProduct);
    setModal('product');
  }
  function openCategory(category = null) {
    setEditing(category); setCategoryForm(category ? { code: category.code || '', name: category.name || '', description: category.description || '' } : emptyCategory); setModal('category');
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
      <div className="sidebar-bottom"><div className="api-status"><span className="status-dot"/><span>Conectado a la API</span></div><button className="nav-item logout" onClick={() => { setToken(''); setTokenState(''); setUser(null); }}><LogOut size={18}/> Cerrar sesión</button></div>
    </aside>
    <main className="main-content">
      <header className="topbar"><div><span className="eyebrow">GESTIÓN COMERCIAL</span><h1>{nav.find(n => n.id === section)?.label || 'Resumen'}</h1></div><div className="topbar-actions"><button className="icon-button" onClick={loadData} title="Actualizar datos"><RefreshCw size={17}/></button><div className="user-chip"><div className="avatar">{String(user?.name || user?.username || user?.email || 'U').slice(0,1).toUpperCase()}</div><div><strong>{user?.name || user?.username || user?.email || 'Usuario'}</strong><small>{(user?.roles || user?.authorities || ['Usuario']).map?.(r => r.name || r.authority || r) .join(', ') || 'Sesión activa'}</small></div></div></div></header>
      {error && <div className="alert error"><CircleAlert size={18}/><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
      {notice && <div className="alert success"><Activity size={18}/><span>{notice}</span><button onClick={() => setNotice('')}>×</button></div>}
      {section === 'overview' && <Overview products={products} categories={categories} stockTotal={stockTotal} inventoryValue={inventoryValue} purchases={purchases} sales={sales} movements={movements} setSection={setSection} loading={loading}/>}
      {section === 'products' && <section className="panel"><PanelHeading title="Catálogo de productos" subtitle="Administra productos, unidades y propiedades dinámicas." action="Nuevo producto" onAction={() => openProduct()} icon={PackagePlus}/><div className="toolbar"><div className="search-box"><Search size={17}/><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por nombre o SKU..."/></div><span className="muted">{visibleProducts.length} productos</span></div><ProductsTable products={visibleProducts} categories={categories} onEdit={openProduct}/></section>}
      {section === 'categories' && <section className="panel"><PanelHeading title="Categorías" subtitle="Organiza el catálogo y define la estructura de tus productos." action="Nueva categoría" onAction={() => openCategory()} icon={Tags}/><CategoriesTable categories={categories} onEdit={openCategory}/></section>}
      {section === 'purchases' && <section className="panel"><PanelHeading title="Compras y entradas" subtitle="Registra órdenes de compra para abastecer el almacén." action="Nueva compra" onAction={() => { setPurchaseForm({...emptyPurchase, warehouseId: pickId(warehouses[0])}); setModal('purchase'); }} icon={ArrowDownToLine}/><DataTable rows={purchases} columns={[['number','Documento'],['status','Estado'],['supplierName','Proveedor'],['createdAt','Fecha']]} empty="Todavía no hay órdenes de compra."/></section>}
      {section === 'sales' && <section className="panel"><PanelHeading title="Ventas y salidas" subtitle="Registra ventas mediante el comando transaccional del backend." action="Nueva venta" onAction={() => { setSaleForm({...emptySale, warehouseId: pickId(warehouses[0])}); setModal('sale'); }} icon={ArrowUpFromLine}/><DataTable rows={sales} columns={[['number','Documento'],['status','Estado'],['customerName','Cliente'],['createdAt','Fecha'],['total','Total']]} moneyKey="total" empty="Todavía no hay ventas."/></section>}
      {section === 'inventory' && <section className="panel"><PanelHeading title="Existencias y movimientos" subtitle="Consulta saldos físicos y trazabilidad de los movimientos." action="Actualizar" onAction={loadData} icon={Warehouse}/><div className="stat-strip"><div><span>Unidades en saldo</span><strong>{stockTotal.toLocaleString('es-EC')}</strong></div><div><span>Valor estimado</span><strong>{money(inventoryValue)}</strong></div><div><span>Movimientos cargados</span><strong>{movements.length}</strong></div></div><h3 className="subheading">Saldos por producto</h3><DataTable rows={balances} columns={[['productName','Producto'],['sku','SKU'],['warehouseName','Almacén'],['onHand','Existencia'],['reserved','Reservado'],['available','Disponible']]} empty="No hay saldos de inventario para mostrar."/><h3 className="subheading">Últimos movimientos</h3><DataTable rows={movements.slice(0, 10)} columns={[['movementType','Movimiento'],['productName','Producto'],['quantity','Cantidad'],['createdAt','Fecha'],['reference','Documento']]} empty="No hay movimientos registrados."/></section>}
      {section === 'reports' && <section className="panel"><PanelHeading title="Balance de inventario" subtitle="Compara compras, ventas y saldo por rango de fechas." icon={ClipboardList}/><form className="report-filters" onSubmit={runReport}><Field label="Almacén" required><select required value={reportForm.warehouseId} onChange={e => setReportForm({...reportForm, warehouseId:e.target.value})}><option value="">Seleccionar almacén</option>{warehouses.map(w => <option key={pickId(w)} value={pickId(w)}>{pickName(w)}</option>)}</select></Field><Field label="Desde" required><input type="date" required value={reportForm.from} onChange={e => setReportForm({...reportForm, from:e.target.value})}/></Field><Field label="Hasta" required><input type="date" min={reportForm.from} required value={reportForm.to} onChange={e => setReportForm({...reportForm, to:e.target.value})}/></Field><Field label="Producto (opcional)"><select value={reportForm.productId} onChange={e => setReportForm({...reportForm, productId:e.target.value})}><option value="">Todos los productos</option>{products.map(p => <option key={pickId(p)} value={pickId(p)}>{p.name} · {p.sku}</option>)}</select></Field><button className="button primary" disabled={loading}><Search size={16}/> Consultar balance</button></form><DataTable rows={report} columns={[['productName','Producto'],['sku','SKU'],['purchasedQuantity','Comprado'],['soldQuantity','Vendido'],['onHand','Saldo actual'],['openingQuantity','Saldo inicial']]} empty="Selecciona un almacén y consulta un periodo para ver el balance."/></section>}
    </main>
    {modal && <Modal title={{product:editing ? 'Editar producto' : 'Crear producto',category:editing ? 'Editar categoría' : 'Crear categoría',purchase:'Registrar compra',sale:'Registrar venta'}[modal]} onClose={() => { setModal(''); setEditing(null); }}><>
      {modal === 'product' && <form className="form-grid" onSubmit={submitProduct}><Field label="Nombre del producto" required><input required maxLength="160" value={productForm.name} onChange={e => setProductForm({...productForm,name:e.target.value})} placeholder="Ej. Teclado mecánico"/></Field><Field label="SKU / código" required><input required maxLength="80" value={productForm.sku} onChange={e => setProductForm({...productForm,sku:e.target.value})} placeholder="Ej. TEC-001"/></Field><Field label="Categoría"><select value={productForm.categoryId} onChange={e => setProductForm({...productForm,categoryId:e.target.value})}><option value="">Sin categoría</option>{categories.map(c => <option key={pickId(c)} value={pickId(c)}>{pickName(c)}</option>)}</select></Field><Field label="Unidad de medida"><select value={productForm.unitOfMeasureId} onChange={e => setProductForm({...productForm,unitOfMeasureId:e.target.value})}><option value="">Seleccionar unidad</option>{units.map(u => <option key={pickId(u)} value={pickId(u)}>{pickName(u)}</option>)}</select></Field><Field label="Tipo"><select value={productForm.kind} onChange={e => setProductForm({...productForm,kind:e.target.value})}><option value="PRODUCT">Producto físico</option><option value="SERVICE">Servicio</option></select></Field><Field label="Descripción"><input value={productForm.description} onChange={e => setProductForm({...productForm,description:e.target.value})} maxLength="1000"/></Field><Field label="Propiedades dinámicas (JSON)" wide><textarea rows="5" spellCheck="false" value={productForm.attributes} onChange={e => setProductForm({...productForm,attributes:e.target.value})} placeholder={'{\n  "color": "Negro",\n  "garantiaMeses": 12\n}'}/></Field><FormActions loading={loading} onCancel={() => setModal('')}/></form>}
      {modal === 'category' && <form className="form-grid" onSubmit={submitCategory}><Field label="Nombre" required><input required maxLength="120" value={categoryForm.name} onChange={e => setCategoryForm({...categoryForm,name:e.target.value})}/></Field><Field label="Código" required><input required maxLength="50" value={categoryForm.code} onChange={e => setCategoryForm({...categoryForm,code:e.target.value})}/></Field><Field label="Descripción" wide><textarea rows="3" value={categoryForm.description} onChange={e => setCategoryForm({...categoryForm,description:e.target.value})}/></Field><FormActions loading={loading} onCancel={() => setModal('')}/></form>}
      {modal === 'purchase' && <form className="form-grid" onSubmit={submitPurchase}><Field label="Almacén" required><select required value={purchaseForm.warehouseId} onChange={e => setPurchaseForm({...purchaseForm,warehouseId:e.target.value})}><option value="">Seleccionar almacén</option>{warehouses.map(w=><option key={pickId(w)} value={pickId(w)}>{pickName(w)}</option>)}</select></Field><Field label="Producto" required><select required value={purchaseForm.productId} onChange={e => setPurchaseForm({...purchaseForm,productId:e.target.value})}><option value="">Seleccionar producto</option>{products.map(p=><option key={pickId(p)} value={pickId(p)}>{p.name} · {p.sku}</option>)}</select></Field><Field label="Cantidad" required><input type="number" min="0.001" step="0.001" required value={purchaseForm.quantity} onChange={e => setPurchaseForm({...purchaseForm,quantity:e.target.value})}/></Field><Field label="Costo unitario" required><input type="number" min="0" step="0.0001" required value={purchaseForm.unitCost} onChange={e => setPurchaseForm({...purchaseForm,unitCost:e.target.value})}/></Field><Field label="Proveedor ID (opcional)"><input value={purchaseForm.supplierId} onChange={e => setPurchaseForm({...purchaseForm,supplierId:e.target.value})} placeholder="UUID o ID de proveedor"/></Field><Field label="Notas"><input value={purchaseForm.notes} onChange={e => setPurchaseForm({...purchaseForm,notes:e.target.value})}/></Field><p className="form-hint wide">La orden se crea y se registra su recepción mediante la API. El stock solo cambia cuando el backend confirma la recepción.</p><FormActions loading={loading} onCancel={() => setModal('')}/></form>}
      {modal === 'sale' && <form className="form-grid" onSubmit={submitSale}><Field label="Almacén" required><select required value={saleForm.warehouseId} onChange={e => setSaleForm({...saleForm,warehouseId:e.target.value})}><option value="">Seleccionar almacén</option>{warehouses.map(w=><option key={pickId(w)} value={pickId(w)}>{pickName(w)}</option>)}</select></Field><Field label="Producto" required><select required value={saleForm.productId} onChange={e => setSaleForm({...saleForm,productId:e.target.value})}><option value="">Seleccionar producto</option>{products.map(p=><option key={pickId(p)} value={pickId(p)}>{p.name} · {p.sku}</option>)}</select></Field><Field label="Cantidad" required><input type="number" min="0.001" step="0.001" required value={saleForm.quantity} onChange={e => setSaleForm({...saleForm,quantity:e.target.value})}/></Field><Field label="Precio unitario" required><input type="number" min="0" step="0.0001" required value={saleForm.unitPrice} onChange={e => setSaleForm({...saleForm,unitPrice:e.target.value})}/></Field><Field label="Notas" wide><input value={saleForm.notes} onChange={e => setSaleForm({...saleForm,notes:e.target.value})}/></Field><p className="form-hint wide">La API debe validar el precio y el stock disponible en una única transacción; nunca se modifica el saldo directamente desde el navegador.</p><FormActions loading={loading} onCancel={() => setModal('')}/></form>}
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
function ProductsTable({products,categories,onEdit}) { const categoryName = id => categories.find(c=>String(pickId(c))===String(id))?.name || '—'; return <div className="table-wrap"><table><thead><tr><th>Producto</th><th>SKU</th><th>Categoría</th><th>Tipo</th><th>Estado</th><th></th></tr></thead><tbody>{products.map((p,i)=><tr key={pickId(p)||i}><td><div className="product-cell"><span className="product-avatar"><ShoppingBag size={17}/></span><div><strong>{p.name||'Sin nombre'}</strong><small>{p.description||'Sin descripción'}</small></div></div></td><td><code>{p.sku||p.code||'—'}</code></td><td>{p.category?.name||categoryName(p.categoryId)}</td><td>{titleCase(p.kind||'PRODUCT')}</td><td><span className={'pill '+(p.active===false||p.status==='INACTIVE'?'inactive':'active')}>{p.active===false||p.status==='INACTIVE'?'Inactivo':'Activo'}</span></td><td><button className="small-action" onClick={()=>onEdit(p)}>Editar</button></td></tr>)}</tbody></table>{!products.length&&<div className="empty-state">No hay productos que coincidan con la búsqueda.</div>}</div>; }
function CategoriesTable({categories,onEdit}) { return <div className="table-wrap"><table><thead><tr><th>Categoría</th><th>Código</th><th>Descripción</th><th>Productos</th><th></th></tr></thead><tbody>{categories.map((c,i)=><tr key={pickId(c)||i}><td><strong>{c.name||'Sin nombre'}</strong></td><td><code>{c.code||'—'}</code></td><td>{c.description||'—'}</td><td>{c.productCount??'—'}</td><td><button className="small-action" onClick={()=>onEdit(c)}>Editar</button></td></tr>)}</tbody></table>{!categories.length&&<div className="empty-state">Todavía no has creado categorías.</div>}</div>; }
function DataTable({rows,columns,moneyKey,empty}) { return <div className="table-wrap"><table><thead><tr>{columns.map(([key,label])=><th key={key}>{label}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={pickId(row)||row.id||i}>{columns.map(([key])=><td key={key}>{key===moneyKey?money(row[key]):key.toLowerCase().includes('date')||key==='createdAt'?date(row[key]):key==='status'||key==='movementType'?titleCase(row[key]||'—'):row[key]??row.product?.name??row.warehouse?.name??'—'}</td>)}</tr>)}</tbody></table>{!rows.length&&<div className="empty-state">{empty}</div>}</div>; }
function Field({label,required,wide,children}) { return <label className={'field'+(wide?' wide':'')}><span>{label}{required&&<b> *</b>}</span>{children}</label>; }
function FormActions({loading,onCancel}) { return <div className="form-actions wide"><button type="button" className="button secondary" onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading}>{loading?'Guardando…':'Guardar cambios'}</button></div>; }
function Modal({title,onClose,children}) { return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onClose();}}><section className="modal-card"><div className="modal-heading"><h2>{title}</h2><button className="icon-button" onClick={onClose} aria-label="Cerrar">×</button></div>{children}</section></div>; }

export default App;

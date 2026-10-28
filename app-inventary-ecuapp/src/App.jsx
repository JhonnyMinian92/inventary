import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDownToLine, ArrowLeft, ArrowUpFromLine, Boxes, Building2, ChartNoAxesCombined, LayoutDashboard, LogOut, MessageCircle, Plus, RefreshCw, Search, Warehouse } from 'lucide-react';
import { api, getToken, getTokenExpiry, listFrom, listAllPages, setToken } from './api';
import { currentRoute, detailPath, documentTitle, HOME_ROUTE, isPlainLeftClick, loginPath, navigate, nextFromSearch, pathFor, sectionPath, workflowPath } from './router.js';
import LoginPage from './features/auth/LoginPage.jsx';
import { BrandMark } from './components/BrandLogo.jsx';
import ecuappLogo from './assets/ecuapp-logo.png';
import ProductForm from './features/catalog/ProductForm.jsx';
import CategoryForm from './features/catalog/CategoryForm.jsx';
import ProductsPage from './features/catalog/ProductsPage.jsx';
import PurchaseForm from './features/purchasing/PurchaseForm.jsx';
import ReceiptForm from './features/purchasing/ReceiptForm.jsx';
import SaleForm from './features/sales/SaleForm.jsx';
import OperationsCatalogs from './features/operations/OperationsCatalogs.jsx';
import InventoryOperations from './features/inventory/InventoryOperations.jsx';
import ReportsCenter from './features/reports/ReportsCenter.jsx';
import Dashboard from './features/dashboard/Dashboard.jsx';
import WhatsAppOrdersPage from './features/whatsapp/WhatsAppOrdersPage.jsx';
import DataTable from './components/DataTable.jsx';
import PurchaseTable from './features/purchasing/PurchaseTable.jsx';
import SalesTable from './features/sales/SalesTable.jsx';
import useAccessibleDialog from './hooks/useAccessibleDialog.js';
import PurchaseReturns from './features/purchasing/PurchaseReturns.jsx';
import SalesReturns from './features/sales/SalesReturns.jsx';
import { PurchaseOrderDetail, SaleDetail } from './components/TransactionDetails.jsx';
import FeedbackHost from './ui/FeedbackHost.jsx';
import { clearToasts, confirmDialog, toast, useMessageToast } from './ui/feedback.js';
import { SkeletonTable } from './ui/Skeleton.jsx';
import { compactMoney, isInactive, number, titleCase } from './utils/format.js';

const NAV_GROUPS = [
  { label: 'General', items: [{ id: 'overview', label: 'Resumen', icon: LayoutDashboard }] },
  { label: 'Catálogo', items: [{ id: 'products', label: 'Productos', icon: Boxes, permission: 'catalog:read', sections: ['products', 'categories'] }] },
  { label: 'Operaciones', items: [
    { id: 'sales', label: 'Ventas', icon: ArrowUpFromLine, permission: 'sales:read' },
    { id: 'whatsapp', label: 'Pedidos WhatsApp', icon: MessageCircle, permission: 'sales:read', badge: 'whatsapp' },
    { id: 'purchases', label: 'Compras', icon: ArrowDownToLine, permission: 'purchasing:read' },
    { id: 'inventory', label: 'Inventario', icon: Warehouse, permission: 'inventory:read' }
  ] },
  { label: 'Gestión', items: [
    { id: 'partners', label: 'Almacenes y contactos', icon: Building2 },
    { id: 'reports', label: 'Reportes', icon: ChartNoAxesCombined, permission: 'reports:financial' }
  ] }
];

const SECTION_TITLES = { overview: 'Resumen', products: 'Productos', categories: 'Productos', sales: 'Ventas', whatsapp: 'Pedidos WhatsApp', purchases: 'Compras', inventory: 'Inventario', partners: 'Almacenes y contactos', reports: 'Reportes' };
const VALID_SECTIONS = new Set(Object.keys(SECTION_TITLES));
const WORKFLOW_MODALS = new Set(['product', 'category', 'purchase', 'sale', 'receive']);
const WORKFLOW_SECTIONS = { product: 'products', category: 'categories', purchase: 'purchases', sale: 'sales', receive: 'purchases' };
const WORKFLOW_TITLES = { product: ['Nuevo producto', 'Editar producto'], category: ['Nueva categoría', 'Editar categoría'], purchase: ['Nueva compra'], sale: ['Nueva venta'], receive: ['Registrar recepción'] };
const DETAIL_SECTIONS = { purchase: 'purchases', sale: 'sales', product: 'products', whatsapp: 'whatsapp' };
const MODAL_DETAILS = new Set(['purchase', 'sale']);

function routeState() {
  const route = currentRoute();
  return {
    section: VALID_SECTIONS.has(route?.section) ? route.section : 'overview',
    workflow: route?.workflow || '',
    entityId: route?.workflow ? route.params.id || '' : '',
    detail: route?.detail ? { kind: route.detail, id: route.params.id } : null
  };
}

function pickId(x) { return x?.id ?? x?.productId ?? x?.categoryId ?? x?.warehouseId ?? ''; }
function pickName(x) { return x?.name ?? x?.description ?? x?.sku ?? String(pickId(x)); }
function quantity(x) { return x?.onHand ?? x?.quantity ?? x?.available ?? x?.stock ?? 0; }

function App() {
  const [token, setTokenState] = useState(getToken());
  const [user, setUser] = useState(null);
  const [section, setSection] = useState(() => routeState().section);
  const [routeEntityId, setRouteEntityId] = useState(() => routeState().entityId);
  const [detailRoute, setDetailRoute] = useState(() => routeState().detail);
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
  const [whatsappPending, setWhatsappPending] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [purchaseDetail, setPurchaseDetail] = useState(null);
  const [saleDetail, setSaleDetail] = useState(null);
  const [saleToCancel, setSaleToCancel] = useState(null);
  const [saleCancelReason, setSaleCancelReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [hasLoadedData, setHasLoadedData] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [apiStatus, setApiStatus] = useState('checking');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState(() => routeState().workflow);
  const [editing, setEditing] = useState(null);
  const [inventoryFilter, setInventoryFilter] = useState({ warehouseId: '', productId: '' });

  useMessageToast(error, setError, 'error');
  useMessageToast(notice, setNotice, 'success');

  // Mientras /auth/me no responde no se ocultan opciones; el backend aplica los permisos en cualquier caso.
  const can = useCallback(permission => !permission || !user || (user.permissions || []).includes(permission), [user]);

  const expireSession = useCallback((message = 'La sesión expiró o ya no es válida. Inicia sesión nuevamente.') => {
    setToken('');
    setTokenState('');
    setUser(null);
    setApiStatus('error');
    toast.info(message);
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    setRefreshKey(key => key + 1);
    const tasks = [
      ['products', listAllPages(api.products, { size: 100 })], ['categories', listAllPages(api.categories, { size: 100 })], ['units', api.units()],
      ['warehouses', listAllPages(api.warehouses, { size: 100 })], ['suppliers', listAllPages(api.suppliers, { size: 100 })],
      ['customers', listAllPages(api.customers, { size: 100 })], ['balances', listAllPages(api.balances, { size: 100 })],
      // Solo los movimientos más recientes: el historial completo se consulta con filtros o en el kárdex.
      ['movements', api.movements({ size: 100 })], ['purchases', listAllPages(api.purchaseOrders, { size: 100 })],
      ['sales', listAllPages(api.sales, { size: 100 })], ['valuation', api.inventoryValuationReport({ size: 100 })],
      ['whatsapp', api.whatsappOrders({ status: 'AWAITING_PAYMENT', size: 1 })]
    ];
    const results = await Promise.allSettled(tasks.map(t => t[1]));
    if (results.some(result => result.status === 'rejected' && result.reason?.status === 401)) {
      expireSession();
      setHasLoadedData(true);
      setLoading(false);
      return;
    }
    setApiStatus(results.some(result => result.status === 'fulfilled') ? 'connected' : 'error');
    const failed = [];
    results.forEach((result, i) => {
      const [key] = tasks[i];
      if (key === 'valuation') { setValuation(result.status === 'fulfilled' ? result.value : null); return; }
      if (key === 'whatsapp') { setWhatsappPending(result.status === 'fulfilled' ? Number(result.value?.page?.totalElements ?? 0) : null); return; }
      if (result.status === 'fulfilled') {
        const value = listFrom(result.value);
        ({ products: setProducts, categories: setCategories, units: setUnits, warehouses: setWarehouses, suppliers: setSuppliers, customers: setCustomers, balances: setBalances, movements: setMovements, purchases: setPurchases, sales: setSales })[key](value);
      } else if (['products', 'categories', 'warehouses', 'balances'].includes(key) && result.reason?.status !== 403) failed.push(result.reason?.message || 'error');
    });
    if (failed.length) setError(results.every(result => result.status === 'rejected') ? 'No hay conexión con el servidor.' : 'No se pudieron cargar algunos datos: ' + [...new Set(failed)].join(' · '));
    setHasLoadedData(true);
    setLoading(false);
  }, [expireSession]);

  useEffect(() => {
    if (!token) return;
    api.me().then(setUser).catch(e => { if (e.status === 401) expireSession(); else setError(e.message); });
    loadData();
  }, [token, loadData, expireSession]);

  // Cierra la sesión en el instante en que vence el token, sin esperar a un 401.
  useEffect(() => {
    if (!token) return undefined;
    const expiresAt = getTokenExpiry();
    if (!expiresAt) return undefined;
    const timer = window.setTimeout(() => expireSession('Tu sesión expiró por seguridad. Inicia sesión nuevamente.'), Math.max(0, expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [token, expireSession]);

  const syncFromLocation = useCallback(() => {
    const route = currentRoute();
    if (!route || route.name === 'login') return;
    const next = routeState();
    setSection(next.section);
    setModal(next.workflow);
    setRouteEntityId(next.entityId);
    setDetailRoute(next.detail);
    if (!next.detail) { setPurchaseDetail(null); setSaleDetail(null); }
    if (next.workflow === 'receive') setSelectedOrder(null);
    if (window.location.pathname !== route.path) window.history.replaceState(window.history.state, '', route.path);
  }, []);

  useEffect(() => {
    window.addEventListener('popstate', syncFromLocation);
    return () => window.removeEventListener('popstate', syncFromLocation);
  }, [syncFromLocation]);

  // Sin sesión, toda ruta lleva a /iniciar-sesion?destino=...; con sesión, el login y las rutas desconocidas llevan al destino o al resumen.
  useEffect(() => {
    const route = currentRoute();
    if (!token) {
      if (route?.name !== 'login') navigate(loginPath(route ? route.path : ''), { replace: true });
      document.title = documentTitle({ title: 'Iniciar sesión' });
      return;
    }
    if (!route || route.name === 'login') {
      const target = route?.name === 'login' ? nextFromSearch(window.location.search) : '';
      navigate(target || pathFor(HOME_ROUTE), { replace: true });
    }
    syncFromLocation();
  }, [token, syncFromLocation]);

  useEffect(() => {
    if (!token) return;
    const route = currentRoute();
    if (route && route.name !== 'login') document.title = documentTitle(route);
  }, [token, section, modal, detailRoute, routeEntityId]);

  const navigateSection = useCallback(async nextSection => {
    const targetSection = VALID_SECTIONS.has(nextSection) ? nextSection : 'overview';
    if ((WORKFLOW_MODALS.has(modal) || currentRoute()?.secondary) && !(await confirmDialog({ title: '¿Salir del formulario?', message: 'Los cambios que no hayas guardado se perderán.', confirmLabel: 'Salir', cancelLabel: 'Seguir editando', tone: 'danger' }))) return;
    setSection(targetSection);
    setModal('');
    setRouteEntityId('');
    setDetailRoute(null);
    setEditing(null);
    setSelectedOrder(null);
    navigate(sectionPath(targetSection));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [modal]);

  function startWorkflow(workflow, entityId = '') {
    const targetSection = WORKFLOW_SECTIONS[workflow] || 'overview';
    setSection(targetSection);
    setModal(workflow);
    setRouteEntityId(entityId ? String(entityId) : '');
    setDetailRoute(null);
    navigate(workflowPath(workflow, entityId ? String(entityId) : ''), { state: { workflow } });
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function closeWorkflow() {
    if (loading) return;
    const parentSection = WORKFLOW_SECTIONS[modal] || section;
    setModal('');
    setRouteEntityId('');
    setEditing(null);
    setSelectedOrder(null);
    setSection(parentSection);
    navigate(sectionPath(parentSection), { replace: true });
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  useEffect(() => {
    if (modal === 'product' && routeEntityId && products.length) {
      setEditing(products.find(item => String(pickId(item)) === String(routeEntityId)) || null);
    } else if (modal === 'category' && routeEntityId && categories.length) {
      setEditing(categories.find(item => String(pickId(item)) === String(routeEntityId)) || null);
    }
  }, [modal, routeEntityId, products, categories]);

  useEffect(() => {
    if (modal !== 'receive' || !routeEntityId || String(pickId(selectedOrder)) === String(routeEntityId)) return undefined;
    let active = true;
    setLoading(true);
    api.purchaseOrder(routeEntityId)
      .then(detail => { if (active) setSelectedOrder(detail); })
      .catch(err => {
        if (!active) return;
        if (err?.status === 401) expireSession();
        else setError(err?.message || 'No se pudo cargar la orden para recibirla.');
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [modal, routeEntityId, expireSession]);

  // /compras/:id y /ventas/:id abren su detalle en una ventana sobre el listado; productos y pedidos usan paneles propios.
  useEffect(() => {
    if (!token || !detailRoute || !MODAL_DETAILS.has(detailRoute.kind)) return undefined;
    let active = true;
    const isPurchase = detailRoute.kind === 'purchase';
    setLoading(true);
    (isPurchase ? api.purchaseOrder(detailRoute.id) : api.sale(detailRoute.id))
      .then(detail => {
        if (!active) return;
        if (isPurchase) setPurchaseDetail(detail); else setSaleDetail(detail);
        setModal(isPurchase ? 'purchaseDetail' : 'saleDetail');
      })
      .catch(err => {
        if (!active) return;
        if (err?.status === 401) { expireSession(); return; }
        setError(err?.message || (isPurchase ? 'No se pudo cargar el detalle de la orden.' : 'No se pudo cargar el detalle de la venta.'));
        setDetailRoute(null);
        navigate(sectionPath(isPurchase ? 'purchases' : 'sales'), { replace: true });
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token, detailRoute, expireSession]);

  const isWorkflow = WORKFLOW_MODALS.has(modal);
  const refreshing = loading && hasLoadedData;
  const stockTotal = balances.reduce((sum, row) => sum + Number(quantity(row)), 0);
  const valuationTotals = Array.isArray(valuation?.totals) ? valuation.totals : [];
  const inventoryValueLabel = !valuation ? '—' : valuationTotals.length === 1 ? compactMoney(valuationTotals[0].totalValue) : valuationTotals.length > 1 ? 'Varias monedas' : compactMoney(0);
  const dashboardData = useMemo(() => ({ products, categories, balances, movements, purchases, sales, warehouses, valuation }), [products, categories, balances, movements, purchases, sales, warehouses, valuation]);

  async function runAction(action, success) {
    const wasWorkflow = WORKFLOW_MODALS.has(modal);
    const workflowSection = WORKFLOW_SECTIONS[modal] || section;
    setLoading(true);
    try {
      await action();
      setNotice(success);
      if (!detailRoute || !MODAL_DETAILS.has(detailRoute.kind)) setModal('');
      setEditing(null);
      if (wasWorkflow) {
        setRouteEntityId('');
        setSelectedOrder(null);
        setSection(workflowSection);
        navigate(sectionPath(workflowSection), { replace: true });
      }
      await loadData();
      return true;
    } catch (e) {
      if (e.status === 401) expireSession();
      else setError(e.message || 'No se pudo completar la operación.');
      return false;
    } finally { setLoading(false); }
  }

  async function submitProduct(form) {
    if (!String(form.name || '').trim() || !String(form.sku || '').trim()) {
      setError('Escribe el nombre y el SKU del producto.');
      return;
    }
    if (!form.categoryId) {
      setError('Selecciona una categoría.');
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
    if (String(form.costPrice ?? '').trim() === '' || String(form.salePrice ?? '').trim() === '' ||
        !Number.isFinite(data.costPrice) || data.costPrice < 0 || !Number.isFinite(data.salePrice) || data.salePrice < 0) {
      setError('El costo y el precio de venta deben ser números válidos no negativos.');
      return;
    }
    await runAction(() => editing ? api.updateProduct(pickId(editing), data) : api.createProduct(data), editing ? 'Producto actualizado.' : 'Producto creado.');
  }

  async function submitCategory(form) {
    if (!String(form.name || '').trim()) {
      setError('Escribe un nombre para la categoría.');
      return;
    }
    const data = { name: form.name.trim(), description: form.description.trim() };
    await runAction(async () => {
      const result = editing ? await api.updateCategory(pickId(editing), data) : await api.createCategory(data);
      const categoryId = result?.id ?? result?.categoryId ?? (editing ? pickId(editing) : null);
      if (!categoryId) throw new Error('La API guardó la categoría pero no devolvió su identificador; no se pudo guardar el esquema de propiedades.');
      await api.saveCategoryAttributes(categoryId, { definitions: Array.isArray(form.definitions) ? form.definitions : [] });
    }, editing ? 'Categoría actualizada.' : 'Categoría creada.');
  }

  async function toggleStatus(kind, item) {
    const activate = isInactive(item);
    const noun = kind === 'product' ? 'producto' : 'categoría';
    const ok = await confirmDialog({
      title: `${activate ? 'Activar' : 'Desactivar'} ${noun}`,
      message: activate ? `${pickName(item)} volverá a estar disponible.` : `${pickName(item)} no podrá usarse en nuevas operaciones. El historial se conserva.`,
      confirmLabel: activate ? 'Activar' : 'Desactivar',
      tone: activate ? 'default' : 'danger'
    });
    if (!ok) return;
    const id = pickId(item);
    const action = kind === 'product' ? (activate ? api.activateProduct : api.deactivateProduct) : (activate ? api.activateCategory : api.deactivateCategory);
    await runAction(() => action(id), `${titleCase(noun)} ${activate ? 'activad' : 'desactivad'}${kind === 'product' ? 'o' : 'a'}.`);
  }

  async function deleteItem(kind, item) {
    if (!item || item.active !== false) return;
    const noun = kind === 'product' ? 'producto' : 'categoría';
    const ok = await confirmDialog({
      title: `Eliminar ${noun}`,
      message: `${pickName(item)} se eliminará definitivamente. Solo es posible si no tiene ${kind === 'product' ? 'existencias, movimientos ni documentos' : 'productos ni referencias'} asociados.`,
      confirmLabel: 'Eliminar', tone: 'danger'
    });
    if (!ok) return;
    if (kind === 'product' && detailRoute?.kind === 'product') closeDetail();
    await runAction(() => kind === 'product' ? api.deleteProduct(pickId(item)) : api.deleteCategory(pickId(item)), `${titleCase(noun)} eliminad${kind === 'product' ? 'o' : 'a'}.`);
  }

  async function logout() {
    try {
      await api.logout();
    } catch (logoutError) {
      toast.warning('La sesión se cerró en este equipo, pero el servidor no confirmó el cierre: ' + (logoutError?.message || 'error desconocido'));
    } finally {
      navigate(loginPath(), { replace: true });
      setToken('');
      setTokenState('');
      setUser(null);
    }
  }

  async function submitPurchase(data) {
    await runAction(() => api.createPurchaseOrder(data), 'Orden creada como borrador.');
  }

  async function cancelPurchase(order) {
    const ok = await confirmDialog({
      title: 'Cancelar orden de compra',
      message: `${order.orderNumber || pickId(order)} · ${order.supplierName || order.supplier?.name || 'Proveedor'}. No modifica existencias y no se puede deshacer.`,
      confirmLabel: 'Cancelar orden', cancelLabel: 'Volver', tone: 'danger'
    });
    if (ok) await runAction(() => api.cancelPurchaseOrder(pickId(order)), 'Orden cancelada.');
  }

  function openDetail(kind, itemOrId) {
    const id = String(typeof itemOrId === 'object' ? pickId(itemOrId) : itemOrId || '');
    if (!id) return;
    setSection(DETAIL_SECTIONS[kind]);
    setModal('');
    navigate(detailPath(kind, id), { state: { detail: kind } });
    setDetailRoute({ kind, id });
  }

  function closeDetail() {
    const parent = DETAIL_SECTIONS[detailRoute?.kind] || section;
    setDetailRoute(null);
    setPurchaseDetail(null);
    setSaleDetail(null);
    setModal(current => current === 'purchaseDetail' || current === 'saleDetail' ? '' : current);
    navigate(sectionPath(parent), { replace: true });
  }

  async function openReceive(order) {
    setLoading(true);
    try {
      const detail = await api.purchaseOrder(pickId(order));
      setSelectedOrder(detail);
      startWorkflow('receive', pickId(detail));
    } catch (err) {
      setError('No se pudo cargar la orden para recibirla: ' + (err?.message || 'error'));
    } finally {
      setLoading(false);
    }
  }

  async function submitReceipt(data) {
    if (!selectedOrder) return;
    const completed = await runAction(() => api.createReceipt(pickId(selectedOrder), data), 'Recepción contabilizada. Existencias actualizadas.');
    if (completed) setSelectedOrder(null);
  }

  async function submitSale(data) {
    await runAction(() => api.createSale(data), 'Venta registrada.');
  }

  function requestSaleCancellation(sale) {
    setSaleToCancel(sale);
    setSaleCancelReason('');
    setModal('cancelSale');
  }

  async function submitSaleCancellation(event) {
    event.preventDefault();
    if (!saleToCancel || !saleCancelReason.trim()) {
      setError('Indica el motivo de la anulación.');
      return;
    }
    const completed = await runAction(() => api.requestSaleCancellation(pickId(saleToCancel), { reason: saleCancelReason.trim() }), 'Anulación solicitada. Debe aprobarla otro usuario autorizado.');
    if (completed) {
      setSaleToCancel(null);
      setSaleCancelReason('');
    }
  }

  async function approveSaleCancellation(sale) {
    const ok = await confirmDialog({ title: 'Aprobar anulación', message: `${sale.saleNumber || pickId(sale)} quedará anulada y sus existencias volverán al inventario.`, confirmLabel: 'Aprobar anulación', tone: 'danger' });
    if (ok) await runAction(() => api.approveSaleCancellation(pickId(sale)), 'Anulación aprobada.');
  }

  async function loadInventoryFilter(event, filterValues = inventoryFilter) {
    event?.preventDefault?.();
    setLoading(true);
    const params = {};
    if (filterValues.warehouseId) params.warehouseId = filterValues.warehouseId;
    if (filterValues.productId) params.productId = filterValues.productId;
    params.size = '100';
    const [balanceResult, movementResult] = await Promise.allSettled([api.balances(params), api.movements(params)]);
    if ([balanceResult, movementResult].some(result => result.status === 'rejected' && result.reason?.status === 401)) {
      expireSession();
      setLoading(false);
      return;
    }
    const failures = [];
    if (balanceResult.status === 'fulfilled') setBalances(listFrom(balanceResult.value));
    else failures.push('saldos: ' + (balanceResult.reason?.message || 'error'));
    if (movementResult.status === 'fulfilled') setMovements(listFrom(movementResult.value));
    else if (movementResult.reason?.status !== 403) failures.push('movimientos: ' + (movementResult.reason?.message || 'error'));
    if (failures.length) setError('No se pudieron aplicar todos los filtros. ' + failures.join(' · '));
    setLoading(false);
  }

  function openProduct(product = null) {
    setEditing(product);
    startWorkflow('product', product ? pickId(product) : '');
  }
  function openCategory(category = null) {
    setEditing(category);
    startWorkflow('category', category ? pickId(category) : '');
  }

  if (!token) return <>
    <LoginPage onLogin={async ({ username, password, remember }) => {
      const result = await api.login(username, password);
      const access = result?.accessToken;
      if (!access) throw new Error('La respuesta de inicio de sesión no contiene accessToken.');
      setToken(access, { persist: remember, expiresAt: result.expiresAt });
      clearToasts();
      setTokenState(access);
    }} />
    <FeedbackHost />
  </>;

  const headerAction = section === 'purchases' && can('purchasing:manage') ? <button type="button" className="button primary" onClick={() => startWorkflow('purchase')}><Plus size={16} />Nueva compra</button>
    : section === 'sales' && can('sales:manage') ? <button type="button" className="button primary" onClick={() => startWorkflow('sale')}><Plus size={16} />Nueva venta</button>
    : null;
  const roleLabel = user?.role ? titleCase(user.role) : 'Sesión activa';
  const displayName = user?.username || 'Usuario';

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><BrandMark size={38} className="brand-mark-svg" /><div><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div></div>
      <nav aria-label="Navegación principal" className="side-nav">{NAV_GROUPS.map(group => {
        const items = group.items.filter(item => can(item.permission));
        if (!items.length) return null;
        return <div className="nav-group" key={group.label}>
          <span className="nav-group-label">{group.label}</span>
          {items.map(item => {
            const current = (item.sections || [item.id]).includes(section);
            const badge = item.badge === 'whatsapp' && whatsappPending > 0 ? whatsappPending : 0;
            return <a key={item.id} href={sectionPath(item.id)} title={item.label} aria-current={current ? 'page' : undefined} className={current ? 'nav-item active' : 'nav-item'}
              onClick={event => { if (!isPlainLeftClick(event)) return; event.preventDefault(); navigateSection(item.id); }}>
              <item.icon size={18} /><span>{item.label}</span>{badge > 0 && <b className="nav-badge" aria-label={`${badge} pendientes`}>{badge > 99 ? '99+' : badge}</b>}
            </a>;
          })}
        </div>;
      })}</nav>
      <div className="sidebar-bottom">
        <div className="sidebar-profile">
          <span className="avatar" aria-hidden="true">{displayName.slice(0, 1).toUpperCase()}</span>
          <span className="profile-copy"><strong>{displayName}</strong><small className={apiStatus === 'error' ? 'offline' : ''}>{apiStatus === 'error' ? 'Sin conexión' : roleLabel}</small></span>
          <button type="button" className="icon-button ghost" onClick={logout} aria-label="Cerrar sesión" title="Cerrar sesión"><LogOut size={17} /></button>
        </div>
        <div className="sidebar-legal"><img src={ecuappLogo} alt="" width="18" height="18" /><span>© {new Date().getFullYear()} EcuApp. Todos los derechos reservados.</span></div>
      </div>
    </aside>
    <main className="main-content">
      <header className="topbar">
        <h1>{isWorkflow ? (WORKFLOW_TITLES[modal]?.[editing && WORKFLOW_TITLES[modal][1] ? 1 : 0] || SECTION_TITLES[section]) : SECTION_TITLES[section] || 'Resumen'}</h1>
        <div className="topbar-actions">
          {!isWorkflow && headerAction}
          {!isWorkflow && <button type="button" className={refreshing ? 'icon-button is-loading' : 'icon-button'} onClick={loadData} disabled={loading} aria-busy={loading} title="Actualizar datos" aria-label="Actualizar datos"><RefreshCw size={17} /></button>}
        </div>
      </header>

      {!isWorkflow && section === 'overview' && <Dashboard data={dashboardData} ready={hasLoadedData} refreshing={refreshing} refreshKey={refreshKey} whatsappPending={whatsappPending} can={can}
        onNavigate={navigateSection} onStartWorkflow={workflow => workflow === 'product' ? openProduct() : startWorkflow(workflow)} onOpenProduct={id => openDetail('product', id)} />}

      {!isWorkflow && (section === 'products' || section === 'categories') && <ProductsPage tab={section === 'categories' ? 'categories' : 'products'}
        products={products} categories={categories} balances={balances} ready={hasLoadedData} refreshing={refreshing} can={can}
        detailId={detailRoute?.kind === 'product' ? detailRoute.id : ''} onNavigateTab={navigateSection}
        onOpenDetail={id => openDetail('product', id)} onCloseDetail={closeDetail}
        product={{ onNew: () => openProduct(), onEdit: openProduct, onToggle: item => toggleStatus('product', item), onDelete: item => deleteItem('product', item) }}
        category={{ onNew: () => openCategory(), onEdit: openCategory, onToggle: item => toggleStatus('category', item), onDelete: item => deleteItem('category', item) }} />}

      {!isWorkflow && section === 'whatsapp' && <WhatsAppOrdersPage can={can} detailId={detailRoute?.kind === 'whatsapp' ? detailRoute.id : ''}
        onOpenDetail={id => openDetail('whatsapp', id)} onCloseDetail={closeDetail} onChanged={loadData} />}

      {!isWorkflow && !hasLoadedData && ['purchases', 'sales', 'inventory', 'partners', 'reports'].includes(section) && <section className="panel"><SkeletonTable rows={8} columns={6} /></section>}
      {hasLoadedData && !isWorkflow && section === 'partners' && <OperationsCatalogs onChanged={loadData} />}
      {hasLoadedData && !isWorkflow && section === 'purchases' && <section className={'panel' + (refreshing ? ' is-refreshing' : '')}>
        <PurchaseTable rows={purchases} loading={loading} onView={order => openDetail('purchase', order)}
          onSubmit={order => runAction(() => api.submitPurchaseOrder(pickId(order)), 'Orden enviada para aprobación.')}
          onApprove={order => runAction(() => api.approvePurchaseOrder(pickId(order)), 'Orden aprobada. Ya puedes registrar la recepción.')}
          onCancel={cancelPurchase} onReceive={openReceive} />
        <PurchaseReturns onChanged={loadData} />
      </section>}
      {hasLoadedData && !isWorkflow && section === 'sales' && <section className={'panel' + (refreshing ? ' is-refreshing' : '')}>
        <SalesTable rows={sales} loading={loading} onView={sale => openDetail('sale', sale)} onRequestCancel={requestSaleCancellation} onApproveCancellation={approveSaleCancellation} />
        <SalesReturns onChanged={loadData} />
      </section>}
      {hasLoadedData && !isWorkflow && section === 'inventory' && <section className={'panel' + (refreshing ? ' is-refreshing' : '')}>
        <form className="report-filters inventory-filters" onSubmit={loadInventoryFilter}>
          <Field label="Almacén"><select value={inventoryFilter.warehouseId} onChange={event => setInventoryFilter(current => ({ ...current, warehouseId: event.target.value }))}><option value="">Todos los almacenes</option>{warehouses.map(warehouse => <option key={pickId(warehouse)} value={pickId(warehouse)}>{pickName(warehouse)}</option>)}</select></Field>
          <Field label="Producto"><select value={inventoryFilter.productId} onChange={event => setInventoryFilter(current => ({ ...current, productId: event.target.value }))}><option value="">Todos los productos</option>{products.map(product => <option key={pickId(product)} value={pickId(product)}>{product.name} · {product.sku}</option>)}</select></Field>
          <button className="button primary" disabled={loading}><Search size={16} /> Filtrar</button>
          <button type="button" className="button secondary" disabled={loading} onClick={() => { const reset = { warehouseId: '', productId: '' }; setInventoryFilter(reset); void loadInventoryFilter({ preventDefault: () => {} }, reset); }}>Limpiar</button>
        </form>
        <div className="stat-strip"><div><span>Unidades</span><strong>{number(stockTotal)}</strong></div><div><span>Valor estimado</span><strong>{inventoryValueLabel}</strong></div><div><span>Movimientos</span><strong>{number(movements.length)}</strong></div></div>
        <h3 className="subheading">Saldos por producto</h3>
        <DataTable rows={balances} columns={[['productName', 'Producto'], ['sku', 'SKU'], ['warehouseCode', 'Almacén'], ['onHand', 'Existencia'], ['reserved', 'Reservado'], ['available', 'Disponible']]} empty="No hay saldos de inventario." />
        <h3 className="subheading">Últimos movimientos</h3>
        <DataTable rows={movements.slice(0, 30)} columns={[['type', 'Movimiento'], { key: 'productName', label: 'Producto', render: row => row.productName || products.find(product => String(pickId(product)) === String(row.productId))?.name || row.sku || '—' }, ['warehouseCode', 'Almacén'], ['quantity', 'Cantidad'], ['occurredAt', 'Fecha'], ['referenceType', 'Documento']]} empty="No hay movimientos registrados." />
        <InventoryOperations products={products} warehouses={warehouses} suppliers={suppliers} onChanged={loadData} />
      </section>}
      {hasLoadedData && !isWorkflow && section === 'reports' && <ReportsCenter warehouses={warehouses} products={products} suppliers={suppliers} onUnauthorized={() => expireSession()} />}

      {isWorkflow && <WorkflowPage onCancel={closeWorkflow}>
        {!hasLoadedData ? <SkeletonTable rows={5} columns={3} /> : <>
          {modal === 'product' && <ProductForm key={'product-' + (pickId(editing) || 'new')} initialValue={editing} categories={categories} units={units} loading={loading} onCancel={closeWorkflow} onSubmit={submitProduct} onError={setError}
            onCategoryCreated={can('catalog:manage') ? category => setCategories(current => [...current, category]) : undefined}
            onUnitCreated={can('catalog:manage') ? unit => setUnits(current => [...current, unit]) : undefined} />}
          {modal === 'category' && <CategoryForm key={'category-' + (pickId(editing) || 'new')} initialValue={editing} loading={loading} onCancel={closeWorkflow} onSubmit={submitCategory} onError={setError} />}
          {modal === 'purchase' && <PurchaseForm products={products} warehouses={warehouses} suppliers={suppliers} loading={loading} onCancel={closeWorkflow} onSubmit={submitPurchase} onError={setError} />}
          {modal === 'sale' && <SaleForm products={products} warehouses={warehouses} customers={customers} balances={balances} loading={loading} onCancel={closeWorkflow} onSubmit={submitSale} onError={setError} />}
          {modal === 'receive' && selectedOrder && <ReceiptForm key={pickId(selectedOrder)} order={selectedOrder} loading={loading} onCancel={closeWorkflow} onSubmit={submitReceipt} />}
          {modal === 'receive' && !selectedOrder && loading && <SkeletonTable rows={4} columns={4} />}
          {modal === 'receive' && !selectedOrder && !loading && <div className="empty-block"><strong>No se pudo abrir la orden.</strong><button type="button" className="button secondary" onClick={closeWorkflow}>Volver a compras</button></div>}
        </>}
      </WorkflowPage>}
    </main>

    {modal === 'purchaseDetail' && purchaseDetail && <Modal title="Orden de compra" busy={loading} onClose={closeDetail}><PurchaseOrderDetail order={purchaseDetail} /></Modal>}
    {modal === 'saleDetail' && saleDetail && <Modal title="Venta" busy={loading} onClose={closeDetail}><SaleDetail sale={saleDetail} /></Modal>}
    {modal === 'cancelSale' && saleToCancel && <Modal title="Solicitar anulación" busy={loading} onClose={() => { setModal(''); setSaleToCancel(null); }}>
      <form className="form-grid" onSubmit={submitSaleCancellation}>
        <p className="form-hint wide">Venta <strong>{saleToCancel.saleNumber || pickId(saleToCancel)}</strong>. Las existencias vuelven al aprobarse.</p>
        <Field label="Motivo" required wide><textarea rows="3" required maxLength="500" value={saleCancelReason} onChange={event => setSaleCancelReason(event.target.value)} placeholder="Describe el motivo" /></Field>
        <FormActions loading={loading} onCancel={() => { setModal(''); setSaleToCancel(null); }} submitLabel="Solicitar anulación" />
      </form>
    </Modal>}
    <FeedbackHost />
  </div>;
}

function WorkflowPage({ onCancel, children }) {
  return <section className="workflow-page">
    <div className="workflow-page-heading"><button type="button" className="button secondary workflow-exit" onClick={onCancel}><ArrowLeft size={16} /> Volver</button></div>
    <div className="workflow-page-body">{children}</div>
  </section>;
}

function Field({ label, required, wide, children }) { return <label className={'field' + (wide ? ' wide' : '')}><span>{label}{required && <b> *</b>}</span>{children}</label>; }
function FormActions({ loading, onCancel, submitLabel = 'Guardar cambios' }) { return <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : submitLabel}</button></div>; }
function Modal({ title, onClose, children, busy }) {
  const dialogRef = useAccessibleDialog(true, onClose);
  return <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="global-modal-title" tabIndex={-1}>
      <div className="modal-heading"><h2 id="global-modal-title">{title}</h2><button type="button" className="icon-button" onClick={onClose} disabled={busy} aria-label="Cerrar ventana">×</button></div>
      {children}
    </section>
  </div>;
}

export default App;

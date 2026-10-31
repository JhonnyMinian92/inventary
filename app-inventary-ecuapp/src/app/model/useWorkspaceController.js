import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { productId, productName, productQuantity } from '../../entities/product/model/product.js';
import { compactMoney, titleCase } from '../../utils/format.js';
import { clearToasts, confirmDialog, toast, useMessageToast } from '../../ui/feedback.js';
import { currentRoute, detailPath, documentTitle, HOME_ROUTE, loginPath, navigate, nextFromSearch, pathFor, sectionPath, workflowPath } from '../router/index.js';
import { useAuth } from '../providers/AuthProvider.jsx';
import { useWorkspaceData } from './useWorkspaceData.js';
import useWorkspaceCommands from './useWorkspaceCommands.js';
import { NAV_GROUPS, SECTION_TITLES, VALID_SECTIONS, WORKFLOW_MODALS, WORKFLOW_SECTIONS, DETAIL_SECTIONS, MODAL_DETAILS } from '../navigation/navigationConfig.js';
import { updateBrowserQuery, readSearchValue } from '../../shared/lib/url-state.js';

function routeState() {
  const route = currentRoute();
  return {
    section: VALID_SECTIONS.has(route?.section) ? route.section : 'overview',
    workflow: route?.workflow || '',
    entityId: route?.workflow ? route.params.id || '' : '',
    detail: route?.detail ? { kind: route.detail, id: route.params.id } : null
  };
}

function initialPage(section) {
  const key = section === 'purchases' || section === 'sales' ? section + '.page' : 'page';
  return Math.max(0, Number(readSearchValue(window.location.search, key, '0')) || 0);
}

export default function useWorkspaceController() {
  const { token, user, apiStatus, can, expireSession, login, logout: logoutSession, authError } = useAuth();
  const [section, setSection] = useState(() => routeState().section);
  const [listPage, setListPage] = useState(() => initialPage(routeState().section));
  const [routeEntityId, setRouteEntityId] = useState(() => routeState().entityId);
  const [detailRoute, setDetailRoute] = useState(() => routeState().detail);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [purchaseDetail, setPurchaseDetail] = useState(null);
  const [saleDetail, setSaleDetail] = useState(null);
  const [saleToCancel, setSaleToCancel] = useState(null);
  const [saleCancelReason, setSaleCancelReason] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState(() => routeState().workflow);
  const [editing, setEditing] = useState(null);
  const [inventoryFilter, setInventoryFilter] = useState(() => ({
    warehouseId: readSearchValue(window.location.search, 'inventory.warehouseId', ''),
    productId: readSearchValue(window.location.search, 'inventory.productId', '')
  }));

  useMessageToast(error, setError, 'error');
  useMessageToast(notice, setNotice, 'success');

  const workspace = useWorkspaceData({ token, section, modal, listPage, expireSession, onError: setError });

  useEffect(() => { if (authError) setError(authError); }, [authError]);
  useEffect(() => {
    const forbidden = Object.values(workspace.errors).find(item => item.kind === 'forbidden');
    if (forbidden && section !== 'overview') setError('No tienes permisos para consultar esta sección.');
  }, [workspace.errors, section]);

  const syncFromLocation = useCallback(() => {
    const route = currentRoute();
    if (!route || route.name === 'login') return;
    const next = routeState();
    setSection(next.section);
    setListPage(initialPage(next.section));
    setModal(next.workflow);
    setRouteEntityId(next.entityId);
    setDetailRoute(next.detail);
    if (!next.detail) { setPurchaseDetail(null); setSaleDetail(null); }
    if (next.workflow === 'receive') setSelectedOrder(null);
    setInventoryFilter({
      warehouseId: readSearchValue(window.location.search, 'inventory.warehouseId', ''),
      productId: readSearchValue(window.location.search, 'inventory.productId', '')
    });
    if (window.location.pathname !== route.path) window.history.replaceState(window.history.state, '', route.path);
  }, []);

  useEffect(() => {
    window.addEventListener('popstate', syncFromLocation);
    return () => window.removeEventListener('popstate', syncFromLocation);
  }, [syncFromLocation]);

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
    if ((WORKFLOW_MODALS.has(modal) || currentRoute()?.secondary) &&
      !(await confirmDialog({ title: '¿Salir del formulario?', message: 'Los cambios que no hayas guardado se perderán.', confirmLabel: 'Salir', cancelLabel: 'Seguir editando', tone: 'danger' }))) return;
    setSection(targetSection);
    setListPage(0);
    setModal('');
    setRouteEntityId('');
    setDetailRoute(null);
    setEditing(null);
    setSelectedOrder(null);
    navigate(sectionPath(targetSection));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [modal]);

  const changeListPage = useCallback(page => {
    const next = Math.max(0, Number(page) || 0);
    setListPage(next);
    const key = section === 'purchases' || section === 'sales' ? section + '.page' : 'page';
    updateBrowserQuery({ [key]: next || null }, { replace: false });
  }, [section]);

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
    if (actionLoading || workspace.loading) return;
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
    if (!routeEntityId || !['product', 'category'].includes(modal)) return undefined;
    let active = true;
    const load = modal === 'product' ? api.product(routeEntityId) : api.category(routeEntityId);
    load.then(item => { if (active) setEditing(item); })
      .catch(loadError => {
        if (!active) return;
        if (loadError?.status === 401) expireSession();
        else setError(loadError?.message || 'No se pudo cargar el registro para editar.');
      });
    return () => { active = false; };
  }, [modal, routeEntityId, expireSession]);

  useEffect(() => {
    if (modal !== 'receive' || !routeEntityId || String(productId(selectedOrder)) === String(routeEntityId)) return undefined;
    let active = true;
    setActionLoading(true);
    api.purchaseOrder(routeEntityId)
      .then(detail => { if (active) setSelectedOrder(detail); })
      .catch(loadError => {
        if (!active) return;
        if (loadError?.status === 401) expireSession();
        else setError(loadError?.message || 'No se pudo cargar la orden para recibirla.');
      })
      .finally(() => { if (active) setActionLoading(false); });
    return () => { active = false; };
  }, [modal, routeEntityId, expireSession]);

  useEffect(() => {
    if (!token || !detailRoute || !MODAL_DETAILS.has(detailRoute.kind)) return undefined;
    let active = true;
    const isPurchase = detailRoute.kind === 'purchase';
    setActionLoading(true);
    (isPurchase ? api.purchaseOrder(detailRoute.id) : api.sale(detailRoute.id))
      .then(detail => {
        if (!active) return;
        if (isPurchase) setPurchaseDetail(detail); else setSaleDetail(detail);
        setModal(isPurchase ? 'purchaseDetail' : 'saleDetail');
      })
      .catch(loadError => {
        if (!active) return;
        if (loadError?.status === 401) { expireSession(); return; }
        setError(loadError?.message || (isPurchase ? 'No se pudo cargar el detalle de la orden.' : 'No se pudo cargar el detalle de la venta.'));
        setDetailRoute(null);
        navigate(sectionPath(isPurchase ? 'purchases' : 'sales'), { replace: true });
      })
      .finally(() => { if (active) setActionLoading(false); });
    return () => { active = false; };
  }, [token, detailRoute, expireSession]);

  function closeDetail() {
    const parent = DETAIL_SECTIONS[detailRoute?.kind] || section;
    setDetailRoute(null);
    setPurchaseDetail(null);
    setSaleDetail(null);
    setModal(current => current === 'purchaseDetail' || current === 'saleDetail' ? '' : current);
    navigate(sectionPath(parent), { replace: true });
  }

  function openDetail(kind, itemOrId) {
    const id = String(typeof itemOrId === 'object' ? productId(itemOrId) || itemOrId?.purchaseOrderId || itemOrId?.saleId || itemOrId?.subscriptionId || itemOrId?.orderId || '' : itemOrId || '');
    if (!id) return;
    setSection(DETAIL_SECTIONS[kind]);
    setModal('');
    navigate(detailPath(kind, id), { state: { detail: kind } });
    setDetailRoute({ kind, id });
  }

  function openProduct(product = null) {
    setEditing(product);
    startWorkflow('product', product ? productId(product) : '');
  }
  function openCategory(category = null) {
    setEditing(category);
    startWorkflow('category', category ? (category.id ?? category.categoryId ?? '') : '');
  }

  async function handleLogout() {
    const logoutError = await logoutSession();
    if (logoutError) toast.warning('La sesión se cerró en este equipo, pero el servidor no confirmó el cierre: ' + (logoutError?.message || 'error desconocido'));
    navigate(loginPath(), { replace: true });
  }

  function closeSaleCancellation() {
    setModal('');
    setSaleToCancel(null);
    setSaleCancelReason('');
  }

  const commands = useWorkspaceCommands({
    modal, section, detailRoute, editing, selectedOrder, saleToCancel, saleCancelReason, inventoryFilter,
    setActionLoading, setError, setNotice, setModal, setEditing, setRouteEntityId, setSelectedOrder,
    setSection, setSaleToCancel, setSaleCancelReason, setBalances: workspace.setBalances,
    setMovements: workspace.setMovements, setInventoryFilter,
    loadData: workspace.loadData, expireSession, closeDetail, startWorkflow
  });

  const loadInventoryFilter = useCallback((event, filterValues = inventoryFilter) => {
    event?.preventDefault?.();
    setInventoryFilter(filterValues);
    updateBrowserQuery({
      'inventory.warehouseId': filterValues.warehouseId || null,
      'inventory.productId': filterValues.productId || null
    }, { replace: false });
    return commands.loadInventoryFilter({ preventDefault() {} }, filterValues);
  }, [commands, inventoryFilter]);

  const loading = actionLoading || workspace.loading;
  const refreshing = loading && workspace.hasLoadedData;
  const stockTotal = workspace.balances.reduce((sum, row) => sum + Number(productQuantity(row)), 0);
  const valuationTotals = Array.isArray(workspace.valuation?.totals) ? workspace.valuation.totals : [];
  const inventoryValueLabel = !workspace.valuation ? '—' :
    valuationTotals.length === 1 ? compactMoney(valuationTotals[0].totalValue) :
    valuationTotals.length > 1 ? 'Varias monedas' : compactMoney(0);
  const dashboardData = useMemo(() => ({
    products: workspace.products, categories: workspace.categories, balances: workspace.balances,
    movements: workspace.movements, purchases: workspace.purchases, sales: workspace.sales,
    warehouses: workspace.warehouses, valuation: workspace.valuation
  }), [workspace.products, workspace.categories, workspace.balances, workspace.movements,
    workspace.purchases, workspace.sales, workspace.warehouses, workspace.valuation]);

  return {
    auth: { token, user, apiStatus, can, expireSession, login, authError, handleLogout },
    nav: { groups: NAV_GROUPS, sectionTitles: SECTION_TITLES },
    state: {
      section, listPage, routeEntityId, detailRoute, selectedOrder, purchaseDetail, saleDetail,
      saleToCancel, saleCancelReason, actionLoading, error, notice, modal, editing,
      inventoryFilter, loading, refreshing, isWorkflow: WORKFLOW_MODALS.has(modal)
    },
    data: workspace,
    metrics: { stockTotal, inventoryValueLabel, dashboardData },
    actions: {
      navigateSection, changeListPage, startWorkflow, closeWorkflow, openDetail, closeDetail,
      openProduct, openCategory, handleLogout, closeSaleCancellation, loadInventoryFilter,
      setError, setSaleCancelReason, setInventoryFilter
    },
    commands: {
      ...commands,
      submitPurchaseOrder: order => commands.runAction(
        () => api.submitPurchaseOrder(productId(order) || order?.purchaseOrderId),
        'Orden enviada para aprobación.'
      ),
      approvePurchaseOrder: order => commands.runAction(
        () => api.approvePurchaseOrder(productId(order) || order?.purchaseOrderId),
        'Orden aprobada. Ya puedes registrar la recepción.'
      )
    },
    feedback: { clearToasts }
  };
}

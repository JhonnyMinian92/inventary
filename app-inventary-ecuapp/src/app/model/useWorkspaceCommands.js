import { api, listFrom } from '../../api.js';
import { isInactive, titleCase } from '../../utils/format.js';
import { confirmDialog } from '../../ui/feedback.js';
import { detailPath, navigate, sectionPath } from '../../router.js';

const WORKFLOW_MODALS = new Set(['product', 'category', 'purchase', 'sale', 'receive']);
const WORKFLOW_SECTIONS = { product: 'products', category: 'categories', purchase: 'purchases', sale: 'sales', receive: 'purchases' };
const MODAL_DETAILS = new Set(['purchase', 'sale']);

function pickId(value) { return value?.id ?? value?.productId ?? value?.categoryId ?? value?.warehouseId ?? ''; }
function pickName(value) { return value?.name ?? value?.description ?? value?.sku ?? String(pickId(value)); }

export default function useWorkspaceCommands(context) {
  const {
    modal, section, detailRoute, editing, selectedOrder, saleToCancel, saleCancelReason, inventoryFilter,
    setActionLoading, setError, setNotice, setModal, setEditing, setRouteEntityId, setSelectedOrder,
    setSection, setSaleToCancel, setSaleCancelReason, setBalances, setMovements, setInventoryFilter,
    loadData, expireSession, closeDetail, startWorkflow
  } = context;

  async function runAction(action, success) {
    const wasWorkflow = WORKFLOW_MODALS.has(modal);
    const workflowSection = WORKFLOW_SECTIONS[modal] || section;
    setActionLoading(true);
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
    } catch (error) {
      if (error?.status === 401) expireSession();
      else setError(error?.message || 'No se pudo completar la operación.');
      return false;
    } finally {
      setActionLoading(false);
    }
  }

  async function submitProduct(form) {
    if (!String(form.name || '').trim()) { setError('Escribe el nombre del producto.'); return; }
    if (!form.categoryId) { setError('Selecciona una categoría.'); return; }
    const data = {
      name: form.name.trim(), kind: form.kind || 'STOCKED_GOOD', categoryId: form.categoryId,
      marginPercent: Number(form.marginPercent), description: form.description.trim()
    };
    if (form.attributes && typeof form.attributes === 'object' && !Array.isArray(form.attributes)) data.attributes = form.attributes;
    if (form.unitOfMeasureId) data.unitOfMeasureId = form.unitOfMeasureId;
    if (data.kind !== 'STOCKED_GOOD') data.salePrice = Number(form.manualPrice);
    if (data.kind === 'SERVICE') {
      data.serviceBilling = form.serviceBilling || 'ONE_TIME';
      if (data.serviceBilling === 'SUBSCRIPTION') {
        data.billingFrequency = form.billingFrequency;
        data.termMonths = Number(form.termMonths);
      }
    }
    if (!Number.isFinite(data.marginPercent) || data.marginPercent < 0 || data.marginPercent > 1000) {
      setError('La ganancia sugerida debe ser un porcentaje entre 0 y 1000.');
      return;
    }
    await runAction(() => editing ? api.updateProduct(pickId(editing), data) : api.createProduct(data),
      editing ? 'Producto actualizado.' : 'Producto creado.');
  }

  async function submitCategory(form) {
    if (!String(form.name || '').trim()) { setError('Escribe un nombre para la categoría.'); return; }
    const data = { name: form.name.trim(), description: form.description.trim() };
    const definitions = Array.isArray(form.definitions) ? form.definitions : [];
    await runAction(async () => {
      if (!editing) return api.createCategory({ ...data, definitions });
      await api.updateCategory(pickId(editing), data);
      await api.saveCategoryAttributes(pickId(editing), { definitions });
    }, editing ? 'Categoría actualizada.' : 'Categoría creada.');
  }

  async function toggleStatus(kind, item) {
    const activate = isInactive(item);
    const noun = kind === 'product' ? 'producto' : 'categoría';
    const confirmed = await confirmDialog({
      title: `${activate ? 'Activar' : 'Desactivar'} ${noun}`,
      message: activate ? `${pickName(item)} volverá a estar disponible.` : `${pickName(item)} no podrá usarse en nuevas operaciones. El historial se conserva.`,
      confirmLabel: activate ? 'Activar' : 'Desactivar',
      tone: activate ? 'default' : 'danger'
    });
    if (!confirmed) return;
    const id = pickId(item);
    const action = kind === 'product'
      ? (activate ? api.activateProduct : api.deactivateProduct)
      : (activate ? api.activateCategory : api.deactivateCategory);
    await runAction(() => action(id), `${titleCase(noun)} ${activate ? 'activad' : 'desactivad'}${kind === 'product' ? 'o' : 'a'}.`);
  }

  async function deleteItem(kind, item) {
    if (!item || !isInactive(item)) return;
    const noun = kind === 'product' ? 'producto' : 'categoría';
    const confirmed = await confirmDialog({
      title: `Eliminar ${noun}`,
      message: `${pickName(item)} se eliminará definitivamente. Solo es posible si no tiene ${kind === 'product' ? 'existencias, movimientos ni documentos' : 'productos ni referencias'} asociados.`,
      confirmLabel: 'Eliminar', tone: 'danger'
    });
    if (!confirmed) return;
    if (kind === 'product' && detailRoute?.kind === 'product') closeDetail();
    await runAction(() => kind === 'product' ? api.deleteProduct(pickId(item)) : api.deleteCategory(pickId(item)),
      `${titleCase(noun)} eliminad${kind === 'product' ? 'o' : 'a'}.`);
  }

  async function submitPurchase(data) {
    await runAction(() => api.createPurchaseOrder(data), 'Orden creada como borrador.');
  }

  async function cancelPurchase(order) {
    const confirmed = await confirmDialog({
      title: 'Cancelar orden de compra',
      message: `${order.orderNumber || pickId(order)} · ${order.supplierName || order.supplier?.name || 'Proveedor'}. No modifica existencias y no se puede deshacer.`,
      confirmLabel: 'Cancelar orden', cancelLabel: 'Volver', tone: 'danger'
    });
    if (confirmed) await runAction(() => api.cancelPurchaseOrder(pickId(order)), 'Orden cancelada.');
  }

  async function openReceive(order) {
    setActionLoading(true);
    try {
      const detail = await api.purchaseOrder(pickId(order));
      setSelectedOrder(detail);
      startWorkflow('receive', pickId(detail));
    } catch (error) {
      if (error?.status === 401) expireSession();
      else setError('No se pudo cargar la orden para recibirla: ' + (error?.message || 'error'));
    } finally {
      setActionLoading(false);
    }
  }

  async function submitReceipt(data) {
    if (!selectedOrder) return;
    const completed = await runAction(() => api.createReceipt(pickId(selectedOrder), data),
      'Recepción contabilizada. Existencias actualizadas.');
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
    const completed = await runAction(
      () => api.requestSaleCancellation(pickId(saleToCancel), { reason: saleCancelReason.trim() }),
      'Anulación solicitada. Debe aprobarla otro usuario autorizado.');
    if (completed) {
      setSaleToCancel(null);
      setSaleCancelReason('');
    }
  }

  async function approveSaleCancellation(sale) {
    const confirmed = await confirmDialog({
      title: 'Aprobar anulación',
      message: `${sale.saleNumber || pickId(sale)} quedará anulada y sus existencias volverán al inventario.`,
      confirmLabel: 'Aprobar anulación', tone: 'danger'
    });
    if (confirmed) await runAction(() => api.approveSaleCancellation(pickId(sale)), 'Anulación aprobada.');
  }

  async function loadInventoryFilter(event, filterValues = inventoryFilter) {
    event?.preventDefault?.();
    setActionLoading(true);
    try {
      const params = { page: 0, size: 100 };
      if (filterValues.warehouseId) params.warehouseId = filterValues.warehouseId;
      if (filterValues.productId) params.productId = filterValues.productId;
      const [balanceResult, movementResult] = await Promise.allSettled([api.balances(params), api.movements(params)]);
      if ([balanceResult, movementResult].some(result => result.status === 'rejected' && result.reason?.status === 401)) {
        expireSession();
        return;
      }
      const failures = [];
      if (balanceResult.status === 'fulfilled') setBalances(listFrom(balanceResult.value));
      else failures.push('saldos: ' + (balanceResult.reason?.message || 'error'));
      if (movementResult.status === 'fulfilled') setMovements(listFrom(movementResult.value));
      else if (movementResult.reason?.status !== 403) failures.push('movimientos: ' + (movementResult.reason?.message || 'error'));
      if (failures.length) setError('No se pudieron aplicar todos los filtros. ' + failures.join(' · '));
    } finally {
      setActionLoading(false);
    }
  }

  return {
    runAction, submitProduct, submitCategory, toggleStatus, deleteItem,
    submitPurchase, cancelPurchase, openReceive, submitReceipt, submitSale,
    requestSaleCancellation, submitSaleCancellation, approveSaleCancellation, loadInventoryFilter
  };
}

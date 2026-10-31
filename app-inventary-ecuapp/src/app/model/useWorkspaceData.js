import { useCallback, useEffect, useRef, useState } from 'react';
import { api, listFrom } from '../../api.js';

const EMPTY = {
  products: [], categories: [], units: [], warehouses: [], suppliers: [], customers: [],
  balances: [], movements: [], purchases: [], sales: [], valuation: null, whatsappPending: null,
  pendingCounts: { submittedPurchases: 0, receivablePurchases: 0, drafts: 0, cancellationRequests: 0 },
  pageInfo: { purchases: null, sales: null }
};
const FIRST_PAGE_SIZE = 20;
const REFERENCE_PAGE_SIZE = 100;

function pageMeta(payload) {
  return {
    page: Number(payload?.number ?? payload?.page?.number ?? 0),
    size: Number(payload?.size ?? payload?.page?.size ?? 0),
    totalElements: Number(payload?.totalElements ?? payload?.page?.totalElements ?? listFrom(payload).length),
    totalPages: Number(payload?.totalPages ?? payload?.page?.totalPages ?? 1)
  };
}
function totalOf(payload) { return pageMeta(payload).totalElements; }

/**
 * Loads only the datasets needed by the current route/workflow.
 * No whole-history scan is used for operational tables; table pages request bounded server pages.
 */
export function useWorkspaceData({ token, section, modal, listPage = 0, expireSession, onError }) {
  const [data, setData] = useState(EMPTY);
  const [loading, setLoading] = useState(false);
  const [hasLoadedData, setHasLoadedData] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [errors, setErrors] = useState({});
  const requestVersion = useRef(0);

  const loadData = useCallback(async () => {
    const version = ++requestVersion.current;
    if (!token) {
      setData(EMPTY);
      setHasLoadedData(false);
      setLoading(false);
      return;
    }

    setLoading(true);
    setRefreshKey(value => value + 1);
    setErrors({});
    const tasks = [];
    const add = (key, run, map = value => value) => tasks.push({ key, run, map });
    const catalogRefs = modal === 'product' || modal === 'purchase' || modal === 'sale';
    const purchaseRefs = modal === 'purchase';
    const saleRefs = modal === 'sale';
    const inventoryRefs = section === 'inventory';

    // Dashboard cards and charts consume summary reports and small, explicitly bounded recent samples.
    if (section === 'overview') {
      add('products', () => api.products({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('warehouses', () => api.warehouses({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('balances', () => api.balances({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('movements', () => api.movements({ page: 0, size: 8, sort: 'occurredAt,desc' }), listFrom);
      add('valuation', () => api.inventoryValuationReport({ page: 0, size: 1 }));
      add('pendingSubmitted', () => api.purchaseOrders({ status: 'SUBMITTED', page: 0, size: 1 }));
      add('pendingApproved', () => api.purchaseOrders({ status: 'APPROVED', page: 0, size: 1 }));
      add('pendingPartial', () => api.purchaseOrders({ status: 'PARTIALLY_RECEIVED', page: 0, size: 1 }));
      add('pendingDraft', () => api.purchaseOrders({ status: 'DRAFT', page: 0, size: 1 }));
      add('pendingCancellation', () => api.sales({ status: 'CANCELLATION_REQUESTED', page: 0, size: 1 }));
      add('whatsappPending', () => api.whatsappOrders({ status: 'AWAITING_PAYMENT', page: 0, size: 1 }));
    }

    if (section === 'products' || section === 'categories') {
      add('categories', () => api.categories({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('balances', () => api.balances({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }

    if (section === 'purchases') {
      add('purchases', () => api.purchaseOrders({ page: listPage, size: FIRST_PAGE_SIZE, sort: 'orderedAt,desc' }), listFrom);
      add('purchasePage', () => api.purchaseOrders({ page: 0, size: FIRST_PAGE_SIZE, sort: 'orderedAt,desc' }), pageMeta);
    }

    if (section === 'sales') {
      add('sales', () => api.sales({ page: listPage, size: FIRST_PAGE_SIZE, sort: 'soldAt,desc' }), listFrom);
      add('salesPage', () => api.sales({ page: 0, size: FIRST_PAGE_SIZE, sort: 'soldAt,desc' }), pageMeta);
    }

    if (inventoryRefs) {
      add('balances', () => api.balances({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('movements', () => api.movements({ page: 0, size: 30, sort: 'occurredAt,desc' }), listFrom);
      add('products', () => api.products({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('warehouses', () => api.warehouses({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('suppliers', () => api.suppliers({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }

    if (section === 'subscriptions') {
      add('products', () => api.products({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('warehouses', () => api.warehouses({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('customers', () => api.customers({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }

    // Load picklists only when the corresponding write workflow is open.
    if (catalogRefs) {
      add('products', () => api.products({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('warehouses', () => api.warehouses({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }
    if (purchaseRefs) add('suppliers', () => api.suppliers({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    if (saleRefs) {
      add('customers', () => api.customers({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('balances', () => api.balances({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }
    if (modal === 'product') {
      add('categories', () => api.categories({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
      add('units', () => api.units({ page: 0, size: REFERENCE_PAGE_SIZE }), listFrom);
    }

    if (tasks.length === 0) {
      setData(current => ({ ...EMPTY, pendingCounts: current.pendingCounts, pageInfo: current.pageInfo }));
      setHasLoadedData(true);
      setLoading(false);
      return;
    }

    const results = await Promise.allSettled(tasks.map(task => Promise.resolve().then(task.run)));
    if (requestVersion.current !== version) return;
    const unauthorized = results.some(result => result.status === 'rejected' && result.reason?.status === 401);
    if (unauthorized) {
      setLoading(false);
      expireSession();
      return;
    }

    const nextErrors = {};
    const updates = {};
    results.forEach((result, index) => {
      const task = tasks[index];
      if (result.status === 'fulfilled') updates[task.key] = task.map(result.value);
      else if (result.reason?.status === 403) nextErrors[task.key] = { kind: 'forbidden', message: result.reason.message };
      else nextErrors[task.key] = { kind: 'error', message: result.reason?.message || 'No se pudieron cargar los datos.' };
    });

    setData(current => {
      const next = { ...EMPTY, ...current, ...updates };
      next.pendingCounts = {
        submittedPurchases: updates.pendingSubmitted ? totalOf(updates.pendingSubmitted) : current.pendingCounts.submittedPurchases,
        receivablePurchases: (updates.pendingApproved ? totalOf(updates.pendingApproved) : current.pendingCounts.receivablePurchases)
          + (updates.pendingPartial ? totalOf(updates.pendingPartial) : current.pendingCounts.receivablePurchases),
        drafts: updates.pendingDraft ? totalOf(updates.pendingDraft) : current.pendingCounts.drafts,
        cancellationRequests: updates.pendingCancellation ? totalOf(updates.pendingCancellation) : current.pendingCounts.cancellationRequests
      };
      next.whatsappPending = updates.whatsappPending ? totalOf(updates.whatsappPending) : current.whatsappPending;
      next.pageInfo = {
        purchases: updates.purchasePage || current.pageInfo.purchases,
        sales: updates.salesPage || current.pageInfo.sales
      };
      return next;
    });
    setErrors(nextErrors);
    const importantErrors = Object.values(nextErrors).filter(error => error.kind === 'error');
    if (importantErrors.length) onError?.(importantErrors[0].message);
    setHasLoadedData(true);
    setLoading(false);
  }, [token, section, modal, listPage, expireSession, onError]);

  useEffect(() => {
    void loadData();
    return () => { requestVersion.current += 1; };
  }, [loadData]);

  return {
    ...data,
    loading,
    hasLoadedData,
    refreshKey,
    errors,
    loadData,
    setProducts: value => setData(current => ({ ...current, products: typeof value === 'function' ? value(current.products) : value })),
    setCategories: value => setData(current => ({ ...current, categories: typeof value === 'function' ? value(current.categories) : value })),
    setUnits: value => setData(current => ({ ...current, units: typeof value === 'function' ? value(current.units) : value }))
  };
}

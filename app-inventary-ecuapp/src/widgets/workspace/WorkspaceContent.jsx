import { lazy, Suspense } from 'react';
import { SkeletonTable } from '../../shared/ui/feedback/Skeleton.jsx';

const WorkflowPage = lazy(() => import('../../shared/ui/forms/WorkflowPage.jsx'));

// Route-level loading keeps the initial bundle small and downloads each domain
// only when the user enters that workspace or starts its workflow.
const DashboardPage = lazy(() => import('../../pages/dashboard/DashboardPage.jsx'));
const CatalogPage = lazy(() => import('../../pages/catalog/CatalogPage.jsx'));
const PurchasingPage = lazy(() => import('../../pages/purchasing/PurchasingPage.jsx'));
const SalesPage = lazy(() => import('../../pages/sales/SalesPage.jsx'));
const InventoryPage = lazy(() => import('../../pages/inventory/InventoryPage.jsx'));
const PartnersPage = lazy(() => import('../../pages/partners/PartnersPage.jsx'));
const ReportsPage = lazy(() => import('../../pages/reports/ReportsPage.jsx'));
const SubscriptionsPage = lazy(() => import('../../pages/subscriptions/SubscriptionsPage.jsx'));
const WhatsAppOrdersPage = lazy(() => import('../../pages/whatsapp/WhatsAppOrdersPage.jsx'));
const ProductForm = lazy(() => import('../../features/catalog/ProductForm.jsx'));
const CategoryForm = lazy(() => import('../../features/catalog/CategoryForm.jsx'));
const PurchaseForm = lazy(() => import('../../features/purchasing/PurchaseForm.jsx'));
const ReceiptForm = lazy(() => import('../../features/purchasing/ReceiptForm.jsx'));
const SaleForm = lazy(() => import('../../features/sales/SaleForm.jsx'));

function WorkspaceContentView({ model }) {
  const { auth, state, data, actions, commands, metrics } = model;
  if (state.isWorkflow) {
    return <WorkflowPage onCancel={actions.closeWorkflow}>
      {!data.hasLoadedData ? <SkeletonTable rows={5} columns={3} /> : <>
        {state.modal === 'product' && <ProductForm key={'product-' + (state.editing?.id || state.editing?.productId || 'new')}
          initialValue={state.editing} categories={data.categories} units={data.units} loading={state.loading}
          onCancel={actions.closeWorkflow} onSubmit={commands.submitProduct} onError={actions.setError}
          onCategoryCreated={auth.can('catalog:manage') ? category => data.setCategories(current => [...current, category]) : undefined}
          onUnitCreated={auth.can('catalog:manage') ? unit => data.setUnits(current => [...current, unit]) : undefined} />}
        {state.modal === 'category' && <CategoryForm key={'category-' + (state.editing?.id || state.editing?.categoryId || 'new')}
          initialValue={state.editing} loading={state.loading} onCancel={actions.closeWorkflow}
          onSubmit={commands.submitCategory} onError={actions.setError} />}
        {state.modal === 'purchase' && <PurchaseForm products={data.products} warehouses={data.warehouses} suppliers={data.suppliers}
          loading={state.loading} onCancel={actions.closeWorkflow} onSubmit={commands.submitPurchase} onError={actions.setError} />}
        {state.modal === 'sale' && <SaleForm products={data.products} warehouses={data.warehouses} customers={data.customers}
          balances={data.balances} loading={state.loading} onCancel={actions.closeWorkflow}
          onSubmit={commands.submitSale} onError={actions.setError} />}
        {state.modal === 'receive' && state.selectedOrder && <ReceiptForm key={state.selectedOrder.id || state.selectedOrder.purchaseOrderId}
          order={state.selectedOrder} loading={state.loading} onCancel={actions.closeWorkflow} onSubmit={commands.submitReceipt} />}
        {state.modal === 'receive' && !state.selectedOrder && state.loading && <SkeletonTable rows={4} columns={4} />}
        {state.modal === 'receive' && !state.selectedOrder && !state.loading && <div className="empty-block">
          <strong>No se pudo abrir la orden.</strong>
          <button type="button" className="button secondary" onClick={actions.closeWorkflow}>Volver a compras</button>
        </div>}
      </>}
    </WorkflowPage>;
  }

  if (!data.hasLoadedData && ['purchases', 'sales', 'inventory', 'partners', 'reports'].includes(state.section)) {
    return <section className="panel"><SkeletonTable rows={8} columns={6} /></section>;
  }

  switch (state.section) {
    case 'overview':
      return <DashboardPage data={metrics.dashboardData} pendingCounts={data.pendingCounts} ready={data.hasLoadedData}
        refreshing={state.refreshing} refreshKey={data.refreshKey} whatsappPending={data.whatsappPending} can={auth.can}
        onNavigate={actions.navigateSection}
        onStartWorkflow={workflow => workflow === 'product' ? actions.openProduct() : actions.startWorkflow(workflow)}
        onOpenProduct={id => actions.openDetail('product', id)} />;
    case 'products':
    case 'categories':
      return <CatalogPage tab={state.section} products={data.products} categories={data.categories} balances={data.balances}
        ready={data.hasLoadedData} refreshing={state.refreshing} can={auth.can}
        detailId={state.detailRoute?.kind === 'product' ? state.detailRoute.id : ''}
        onNavigateTab={actions.navigateSection} onOpenDetail={id => actions.openDetail('product', id)}
        onCloseDetail={actions.closeDetail}
        product={{ onNew: actions.openProduct, onEdit: actions.openProduct,
          onToggle: item => commands.toggleStatus('product', item), onDelete: item => commands.deleteItem('product', item) }}
        category={{ onNew: actions.openCategory, onEdit: actions.openCategory,
          onToggle: item => commands.toggleStatus('category', item), onDelete: item => commands.deleteItem('category', item) }} />;
    case 'subscriptions':
      return <SubscriptionsPage can={auth.can} products={data.products} customers={data.customers} warehouses={data.warehouses}
        detailId={state.detailRoute?.kind === 'subscription' ? state.detailRoute.id : ''}
        onOpenDetail={id => actions.openDetail('subscription', id)} onCloseDetail={actions.closeDetail} onChanged={data.loadData} />;
    case 'whatsapp':
      return <WhatsAppOrdersPage can={auth.can} detailId={state.detailRoute?.kind === 'whatsapp' ? state.detailRoute.id : ''}
        onOpenDetail={id => actions.openDetail('whatsapp', id)} onCloseDetail={actions.closeDetail} onChanged={data.loadData} />;
    case 'partners':
      return <PartnersPage onChanged={data.loadData} />;
    case 'purchases':
      return <PurchasingPage rows={data.purchases} loading={state.loading} pagination={data.pageInfo.purchases}
        page={state.listPage} onPageChange={actions.changeListPage} onView={order => actions.openDetail('purchase', order)}
        onSubmit={commands.submitPurchaseOrder} onApprove={commands.approvePurchaseOrder}
        onCancel={commands.cancelPurchase} onReceive={commands.openReceive} onChanged={data.loadData} />;
    case 'sales':
      return <SalesPage rows={data.sales} loading={state.loading} pagination={data.pageInfo.sales}
        page={state.listPage} onPageChange={actions.changeListPage} onView={sale => actions.openDetail('sale', sale)}
        onRequestCancel={commands.requestSaleCancellation} onApproveCancellation={commands.approveSaleCancellation}
        onChanged={data.loadData} />;
    case 'inventory':
      return <InventoryPage products={data.products} balances={data.balances} movements={data.movements}
        warehouses={data.warehouses} suppliers={data.suppliers} inventoryFilter={state.inventoryFilter}
        setInventoryFilter={actions.setInventoryFilter} loading={state.loading} stockTotal={metrics.stockTotal}
        inventoryValueLabel={metrics.inventoryValueLabel} onFilter={actions.loadInventoryFilter} onChanged={data.loadData} />;
    case 'reports':
      return <ReportsPage warehouses={data.warehouses} products={data.products} suppliers={data.suppliers}
        onUnauthorized={auth.expireSession} />;
    default:
      return null;
  }
}

export default function WorkspaceContent({ model }) {
  return <Suspense fallback={<section className="panel"><SkeletonTable rows={8} columns={6} label="Cargando módulo" /></section>}>
    <WorkspaceContentView model={model} />
  </Suspense>;
}

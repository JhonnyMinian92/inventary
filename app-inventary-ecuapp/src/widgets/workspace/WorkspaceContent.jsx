import DashboardPage from '../../pages/dashboard/DashboardPage.jsx';
import CatalogPage from '../../pages/catalog/CatalogPage.jsx';
import PurchasingPage from '../../pages/purchasing/PurchasingPage.jsx';
import SalesPage from '../../pages/sales/SalesPage.jsx';
import InventoryPage from '../../pages/inventory/InventoryPage.jsx';
import PartnersPage from '../../pages/partners/PartnersPage.jsx';
import ReportsPage from '../../pages/reports/ReportsPage.jsx';
import SubscriptionsPage from '../../pages/subscriptions/SubscriptionsPage.jsx';
import WhatsAppOrdersPage from '../../pages/whatsapp/WhatsAppOrdersPage.jsx';
import ProductForm from '../../features/catalog/ProductForm.jsx';
import CategoryForm from '../../features/catalog/CategoryForm.jsx';
import PurchaseForm from '../../features/purchasing/PurchaseForm.jsx';
import ReceiptForm from '../../features/purchasing/ReceiptForm.jsx';
import SaleForm from '../../features/sales/SaleForm.jsx';
import WorkflowPage from '../../shared/ui/forms/WorkflowPage.jsx';
import { SkeletonTable } from '../../shared/ui/feedback/Skeleton.jsx';

export default function WorkspaceContent({ model }) {
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

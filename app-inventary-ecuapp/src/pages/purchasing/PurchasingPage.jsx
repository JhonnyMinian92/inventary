import { useState } from 'react';
import { PackageCheck, ReceiptText, RotateCcw } from 'lucide-react';
import PurchaseTable from '../../features/purchasing/PurchaseTable.jsx';
import PurchaseReturns from '../../features/purchasing/PurchaseReturns.jsx';
import GoodsReceipts from '../../features/purchasing/GoodsReceipts.jsx';
import '../../features/operations/operational-forms.css';
import '../../features/returns/returns.css';
import '../../features/operations/operation-workspaces.css';
import '../../features/purchasing/purchasing.css';

export default function PurchasingPage({
  rows, loading, pagination, page, query, statusFilter, onStatusChange, onSearch, onPageChange, onView, onEdit, onDelete, onSubmit, onApprove, onCancel, onReceive, onChanged, can
}) {
  const [tab, setTab] = useState('orders');
  return <section className={'panel' + (loading ? ' is-refreshing' : '')}>
    <nav className="purchasing-tabs" aria-label="Operaciones de compras" role="tablist">
      <button type="button" role="tab" aria-selected={tab === 'orders'} className={tab === 'orders' ? 'active' : ''} onClick={() => setTab('orders')}><ReceiptText size={16}/>Órdenes de compra</button>
      <button type="button" role="tab" aria-selected={tab === 'receipts'} className={tab === 'receipts' ? 'active' : ''} onClick={() => setTab('receipts')}><PackageCheck size={16}/>Recepciones</button>
      <button type="button" role="tab" aria-selected={tab === 'returns'} className={tab === 'returns' ? 'active' : ''} onClick={() => setTab('returns')}><RotateCcw size={16}/>Devoluciones a proveedores</button>
    </nav>
    <div role="tabpanel">
      {tab === 'orders' && <PurchaseTable rows={rows} loading={loading} pagination={pagination} page={page} query={query} onSearch={onSearch}
        onPageChange={onPageChange} onView={onView} onEdit={onEdit} onDelete={onDelete} statusFilter={statusFilter} onStatusChange={onStatusChange} onSubmit={onSubmit} onApprove={onApprove}
        onCancel={onCancel} onReceive={onReceive} can={can} />}
      {tab === 'receipts' && <GoodsReceipts />}
      {tab === 'returns' && <PurchaseReturns onChanged={onChanged} />}
    </div>
  </section>;
}

import PurchaseTable from '../../features/purchasing/PurchaseTable.jsx';
import PurchaseReturns from '../../features/purchasing/PurchaseReturns.jsx';

export default function PurchasingPage({
  rows, loading, pagination, page, onPageChange, onView, onSubmit, onApprove, onCancel, onReceive, onChanged
}) {
  return <section className={'panel' + (loading ? ' is-refreshing' : '')}>
    <PurchaseTable rows={rows} loading={loading} pagination={pagination} page={page}
      onPageChange={onPageChange} onView={onView} onSubmit={onSubmit} onApprove={onApprove}
      onCancel={onCancel} onReceive={onReceive} />
    <PurchaseReturns onChanged={onChanged} />
  </section>;
}

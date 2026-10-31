import SalesTable from '../../features/sales/SalesTable.jsx';
import SalesReturns from '../../features/sales/SalesReturns.jsx';
import '../../features/operations/operational-forms.css';
import '../../features/returns/returns.css';
import '../../features/operations/operation-workspaces.css';

export default function SalesPage({
  rows, loading, pagination, page, onPageChange, onView, onRequestCancel, onApproveCancellation, onChanged
}) {
  return <section className={'panel' + (loading ? ' is-refreshing' : '')}>
    <SalesTable rows={rows} loading={loading} pagination={pagination} page={page}
      onPageChange={onPageChange} onView={onView} onRequestCancel={onRequestCancel}
      onApproveCancellation={onApproveCancellation} />
    <SalesReturns onChanged={onChanged} />
  </section>;
}

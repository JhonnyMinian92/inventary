import { useEffect, useState } from 'react';
import { ReceiptText, RotateCcw, WalletCards } from 'lucide-react';
import SalesTable from '../../features/sales/SalesTable.jsx';
import SalesReturns from '../../features/sales/SalesReturns.jsx';
import RefundsPanel from '../../features/sales/RefundsPanel.jsx';
import '../../features/operations/operational-forms.css';
import '../../features/returns/returns.css';
import '../../features/operations/operation-workspaces.css';
import '../../features/sales/sales-workspace.css';

export default function SalesPage({
  rows, loading, pagination, page, onPageChange, onView, onRequestCancel, onApproveCancellation, onChanged, can
}) {
  const [tab, setTab] = useState(() => window.location.pathname.includes('/devoluciones') ? 'returns' : window.location.pathname.includes('/reembolsos') ? 'refunds' : 'sales');

  useEffect(() => {
    const sync = () => setTab(window.location.pathname.includes('/devoluciones') ? 'returns' : window.location.pathname.includes('/reembolsos') ? 'refunds' : 'sales');
    window.addEventListener('popstate', sync);
    return () => window.removeEventListener('popstate', sync);
  }, []);

  return <section className={'panel' + (loading ? ' is-refreshing' : '')}>
    <nav className="sales-tabs" aria-label="Operaciones de ventas">
      <button type="button" className={tab === 'sales' ? 'active' : ''} aria-pressed={tab === 'sales'} onClick={() => setTab('sales')}><ReceiptText size={16}/>Ventas</button>
      <button type="button" className={tab === 'returns' ? 'active' : ''} aria-pressed={tab === 'returns'} onClick={() => setTab('returns')}><RotateCcw size={16}/>Devoluciones</button>
      <button type="button" className={tab === 'refunds' ? 'active' : ''} aria-pressed={tab === 'refunds'} onClick={() => setTab('refunds')}><WalletCards size={16}/>Reembolsos</button>
    </nav>
    {tab === 'sales' && <div className="sales-list-workspace">
      <div className="sales-list-heading"><div><h3>Ventas registradas</h3><p>Consulta documentos, pagos, clientes y solicitudes de anulación.</p></div><span className="sales-record-count">{numberRows(rows)} registros</span></div>
      <SalesTable rows={rows} loading={loading} pagination={pagination} page={page}
        onPageChange={onPageChange} onView={onView} onRequestCancel={onRequestCancel}
        onApproveCancellation={onApproveCancellation} />
    </div>}
    {tab === 'returns' && <SalesReturns onChanged={onChanged} />}
    {tab === 'refunds' && <RefundsPanel canManage={Boolean(can?.('sales:manage'))} />}
  </section>;
}

function numberRows(rows = []) {
  return new Intl.NumberFormat('es-EC').format(rows.length);
}

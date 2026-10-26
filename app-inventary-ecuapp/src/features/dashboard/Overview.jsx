import { Activity, ArrowDownToLine, ArrowUpFromLine, Boxes, ChartNoAxesCombined, ChevronRight, PackagePlus, Tags, Warehouse } from 'lucide-react';

function titleCase(value = '') { return String(value).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }
function date(value) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? String(value) : parsed.toLocaleDateString('es-EC');
}

function QuickAction({ icon: Icon, title, text, onClick }) {
  return <button type="button" className="quick-action" onClick={onClick}>
    <span className="quick-icon"><Icon size={20}/></span>
    <span><strong>{title}</strong><small>{text}</small></span>
    <ChevronRight size={16}/>
  </button>;
}

export default function Overview({ products = [], categories = [], stockTotal = 0, inventoryValue = '—', purchases = [], sales = [], movements = [], setSection, loading, onNewProduct, onNewCategory, onNewPurchase, onNewSale }) {
  const cards = [
    { label: 'Productos', value: products.length, detail: 'Referencias registradas', icon: Boxes, color: 'violet' },
    { label: 'Categorías', value: categories.length, detail: 'Grupos de catálogo', icon: Tags, color: 'blue' },
    { label: 'Unidades en stock', value: stockTotal.toLocaleString('es-EC'), detail: 'Suma de saldos disponibles', icon: Warehouse, color: 'green' },
    { label: 'Valor estimado', value: inventoryValue, detail: 'Según valoración financiera del backend', icon: ChartNoAxesCombined, color: 'orange' }
  ];
  return <>
    <div className="welcome-row">
      <div><h2>Todo tu inventario, en un solo lugar.</h2><p>Consulta el estado del negocio y accede rápidamente a las operaciones principales.</p></div>
      <button type="button" className="button secondary" onClick={() => setSection('reports')}><ChartNoAxesCombined size={16}/> Ver balance</button>
    </div>
    <div className="stats-grid">{cards.map(card => <div className="stat-card" key={card.label}>
      <div className="stat-top"><span>{card.label}</span><span className={'stat-icon ' + card.color}><card.icon size={19}/></span></div>
      <strong>{card.value}</strong><small>{card.detail}</small>
    </div>)}</div>
    <div className="content-grid">
      <section className="panel quick-panel">
        <div className="section-heading"><div><h3>Acciones rápidas</h3><p>Continúa con una tarea frecuente.</p></div></div>
        <div className="quick-actions">
          <QuickAction icon={PackagePlus} title="Crear producto" text="Añade una referencia al catálogo" onClick={onNewProduct}/>
          <QuickAction icon={Tags} title="Nueva categoría" text="Organiza tus productos" onClick={onNewCategory}/>
          <QuickAction icon={ArrowDownToLine} title="Registrar compra" text="Abastece tu inventario" onClick={onNewPurchase}/>
          <QuickAction icon={ArrowUpFromLine} title="Registrar venta" text="Registra una salida comercial" onClick={onNewSale}/>
        </div>
      </section>
      <section className="panel activity-panel">
        <div className="section-heading"><div><h3>Actividad reciente</h3><p>Últimos movimientos registrados</p></div><button type="button" className="text-button" onClick={() => setSection('inventory')}>Ver todo <ChevronRight size={14}/></button></div>
        {loading ? <div className="empty-state" role="status">Actualizando datos…</div> : movements.length === 0 ? <div className="empty-state"><Activity size={24}/><span>Aún no hay actividad para mostrar.</span></div> : movements.slice(0, 5).map((movement, index) =>
          <div className="activity-row" key={movement.id || index}>
            <div className="activity-icon"><Activity size={16}/></div>
            <div className="activity-copy"><strong>{titleCase(movement.movementType || movement.type || 'Movimiento de inventario')}</strong><small>{movement.productName || movement.product?.name || movement.reference || 'Movimiento registrado'}</small></div>
            <div className="activity-meta"><strong>{movement.quantity ?? movement.delta ?? '—'}</strong><small>{date(movement.createdAt || movement.occurredAt)}</small></div>
          </div>
        )}
      </section>
    </div>
    <div className="bottom-note"><span className="status-dot"/><span>Los saldos y documentos provienen directamente de la API.</span><span className="note-separator">·</span><span>{purchases.length} compras</span><span className="note-separator">·</span><span>{sales.length} ventas</span></div>
  </>;
}

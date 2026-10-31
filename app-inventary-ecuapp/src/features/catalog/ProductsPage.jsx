import { useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Boxes, ChevronLeft, ChevronRight, Download, PackageSearch, Pencil, Plus, Power, Search, Tags, Trash2 } from 'lucide-react';
import { api, listFrom } from '../../api';
import { isPlainLeftClick, sectionPath } from '../../router.js';
import Drawer from '../../ui/Drawer.jsx';
import { Skeleton, SkeletonTable } from '../../ui/Skeleton.jsx';
import { toast } from '../../ui/feedback.js';
import { downloadCsv } from '../../utils/csv.js';
import { dateTime, idOf, isInactive, isoDate, money, movementDirection, movementLabel, number, titleCase } from '../../utils/format.js';
import './catalog.css';

const PAGE_SIZE = 20;
const KIND_LABELS = { STOCKED_GOOD: 'Inventariable', NON_STOCKED_GOOD: 'No inventariable', SERVICE: 'Servicio' };

function SortHeader({ id, label, sort, onSort, align }) {
  const active = sort.key === id;
  const Icon = !active ? ArrowUpDown : sort.dir === 'asc' ? ArrowUp : ArrowDown;
  return <th scope="col" className={align} aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
    <button type="button" className="sort-button" onClick={() => onSort(id)}>{label}<Icon size={13} aria-hidden="true" /></button>
  </th>;
}

function Pagination({ page, total, onPage }) {
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  if (total <= PAGE_SIZE) return null;
  const start = page * PAGE_SIZE + 1;
  return <div className="pagination">
    <span>{start}–{Math.min(total, start + PAGE_SIZE - 1)} de {number(total)}</span>
    <button type="button" className="icon-button" aria-label="Página anterior" disabled={page === 0} onClick={() => onPage(page - 1)}><ChevronLeft size={16} /></button>
    <button type="button" className="icon-button" aria-label="Página siguiente" disabled={page >= pages - 1} onClick={() => onPage(page + 1)}><ChevronRight size={16} /></button>
  </div>;
}

function CatalogTabs({ active, productCount, categoryCount, onNavigate }) {
  const tabs = [['products', 'Productos', productCount, Boxes], ['categories', 'Categorías', categoryCount, Tags]];
  return <nav className="page-tabs" aria-label="Catálogo">
    {tabs.map(([id, label, count, Icon]) => <a key={id} href={sectionPath(id)} aria-current={active === id ? 'page' : undefined}
      onClick={event => { if (!isPlainLeftClick(event)) return; event.preventDefault(); onNavigate(id); }}>
      <Icon size={16} aria-hidden="true" />{label}<span className="tab-count">{number(count)}</span>
    </a>)}
  </nav>;
}

function ProductDrawer({ product, category, balances, can, onClose, onEdit, onToggle, onDelete }) {
  const canMovements = can('inventory:manage') || can('audit:read');
  const [movements, setMovements] = useState(null);
  const productId = idOf(product);
  useEffect(() => {
    if (!canMovements) return undefined;
    let active = true;
    setMovements(null);
    api.movements({ productId, size: 8, sort: 'occurredAt,desc' })
      .then(payload => { if (active) setMovements(listFrom(payload)); })
      .catch(error => { if (active) { setMovements([]); toast.error(error?.message || 'No se pudieron cargar los movimientos.'); } });
    return () => { active = false; };
  }, [productId, canMovements]);
  const rows = balances.filter(row => String(row.productId) === String(productId));
  const onHand = rows.reduce((sum, row) => sum + Number(row.onHand || 0), 0);
  const available = rows.reduce((sum, row) => sum + Number(row.available ?? row.onHand ?? 0), 0);
  const reserved = rows.reduce((sum, row) => sum + Number(row.reserved || 0), 0);
  const max = Math.max(...rows.map(row => Number(row.onHand || 0)), 0) || 1;
  const inactive = isInactive(product);
  const manage = can('catalog:manage');
  return <Drawer open title={product.name} subtitle={`${product.sku}${category ? ' · ' + category : ''}`} onClose={onClose}
    footer={manage && <>
      {inactive && <button type="button" className="button danger-ghost" onClick={() => onDelete(product)}><Trash2 size={15} />Eliminar</button>}
      <button type="button" className="button secondary" onClick={() => onToggle(product)}><Power size={15} />{inactive ? 'Activar' : 'Desactivar'}</button>
      <button type="button" className="button primary" onClick={() => onEdit(product)}><Pencil size={15} />Editar</button>
    </>}>
    <div className="detail-pills"><span className={'pill ' + (inactive ? 'inactive' : 'active')}>{inactive ? 'Inactivo' : 'Activo'}</span><span className="pill neutral">{KIND_LABELS[product.kind] || titleCase(product.kind || 'STOCKED_GOOD')}</span></div>
    <dl className="metric-grid">
      <div><dt>Existencia</dt><dd>{number(onHand)}</dd></div>
      <div><dt>Disponible</dt><dd>{number(available)}</dd></div>
      <div><dt>Reservado</dt><dd>{number(reserved)}</dd></div>
      <div><dt>Precio de venta</dt><dd>{money(product.salePrice)}</dd></div>
      {product.costPrice !== undefined && product.costPrice !== null && <div><dt>Costo (última compra)</dt><dd>{money(product.costPrice)}</dd></div>}
      {product.marginPercent !== undefined && product.marginPercent !== null && <div><dt>Ganancia sugerida</dt><dd>{Number(product.marginPercent)} %</dd></div>}
    </dl>
    {product.description && <p className="drawer-text">{product.description}</p>}
    <h3 className="drawer-subtitle">Existencias por almacén</h3>
    {rows.length ? <ul className="stock-rows">{rows.map(row => <li key={row.warehouseId}>
      <span>{row.warehouseCode}</span>
      <span className="stock-track" aria-hidden="true"><span style={{ width: Math.max(0, Number(row.onHand || 0)) / max * 100 + '%' }} /></span>
      <strong>{number(row.onHand)}</strong>
    </li>)}</ul> : <p className="drawer-empty">Sin existencias registradas.</p>}
    {canMovements && <h3 className="drawer-subtitle">Últimos movimientos</h3>}
    {!canMovements ? null : movements === null ? <div className="drawer-skeleton">{[0, 1, 2].map(index => <Skeleton key={index} height={34} radius={8} />)}</div>
      : movements.length ? <ul className="movement-list compact">{movements.map(movement => {
        const direction = movementDirection(movement.type);
        return <li key={movement.id}>
          <span className={'movement-sign ' + (direction > 0 ? 'in' : direction < 0 ? 'out' : 'neutral')} aria-hidden="true">{direction > 0 ? '+' : direction < 0 ? '−' : '•'}</span>
          <span className="movement-copy"><strong>{movementLabel(movement.type)}</strong><small>{movement.warehouseCode} · {dateTime(movement.occurredAt)}</small></span>
          <span className="movement-meta"><strong>{direction < 0 ? '−' : direction > 0 ? '+' : ''}{number(movement.quantity)}</strong></span>
        </li>;
      })}</ul> : <p className="drawer-empty">Sin movimientos.</p>}
  </Drawer>;
}

function ProductsTab({ products: initialProducts, categories, balances, ready, can, onOpenDetail, onNew, onEdit, onToggle, onDelete }) {
  const [query, setQuery] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [statusFilter, setStatusFilter] = useState('active');
  const [stockFilter, setStockFilter] = useState('');
  const [sort, setSort] = useState({ key: 'name', dir: 'asc' });
  const [page, setPage] = useState(0);
  const [serverProducts, setServerProducts] = useState(initialProducts || []);
  const [serverTotal, setServerTotal] = useState(Number(initialProducts?.length || 0));
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState(query);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    let active = true;
    setListLoading(true);
    setListError('');
    const params = { page, size: PAGE_SIZE, sort: (sort.key === 'price' ? 'salePrice' : sort.key === 'name' ? 'name' : 'name') + ',' + sort.dir };
    if (debouncedQuery) params.q = debouncedQuery;
    if (categoryId) params.categoryId = categoryId;
    if (statusFilter === 'active') params.active = true;
    if (statusFilter === 'inactive') params.active = false;
    api.products(params).then(payload => {
      if (!active) return;
      setServerProducts(listFrom(payload));
      setServerTotal(Number(payload?.totalElements ?? payload?.page?.totalElements ?? listFrom(payload).length));
    }).catch(error => {
      if (!active) return;
      setServerProducts([]);
      setServerTotal(0);
      setListError(error?.status === 403 ? 'No tienes permiso para consultar el catálogo.' : (error?.message || 'No se pudieron cargar los productos.'));
    }).finally(() => { if (active) setListLoading(false); });
    return () => { active = false; };
  }, [page, debouncedQuery, categoryId, statusFilter, sort]);
  const categoryNames = useMemo(() => new Map(categories.map(category => [String(idOf(category)), category.name])), [categories]);
  const stock = useMemo(() => {
    const totals = new Map();
    balances.forEach(row => {
      const current = totals.get(String(row.productId)) || { onHand: 0, available: 0 };
      current.onHand += Number(row.onHand || 0);
      current.available += Number(row.available ?? row.onHand ?? 0);
      totals.set(String(row.productId), current);
    });
    return totals;
  }, [balances]);

  const rows = useMemo(() => {
    const text = query.trim().toLowerCase();
    const filtered = serverProducts.filter(product => {
      const id = String(idOf(product));
      const onHand = stock.get(id)?.onHand || 0;
      if (text && ![product.name, product.sku, product.description].some(value => String(value || '').toLowerCase().includes(text))) return false;
      if (categoryId && String(product.categoryId ?? product.category?.id) !== categoryId) return false;
      if (statusFilter === 'active' && isInactive(product)) return false;
      if (statusFilter === 'inactive' && !isInactive(product)) return false;
      if (stockFilter === 'with' && !(onHand > 0)) return false;
      if (stockFilter === 'without' && onHand > 0) return false;
      return true;
    });
    const value = product => sort.key === 'stock' ? stock.get(String(idOf(product)))?.onHand || 0
      : sort.key === 'price' ? Number(product.salePrice || 0)
      : sort.key === 'category' ? categoryNames.get(String(product.categoryId)) || ''
      : String(product.name || '').toLowerCase();
    return filtered.sort((a, b) => {
      const left = value(a), right = value(b);
      const order = typeof left === 'number' ? left - right : String(left).localeCompare(String(right), 'es');
      return sort.dir === 'asc' ? order : -order;
    });
  }, [serverProducts, stockFilter, sort, stock, categoryNames]);

  useEffect(() => { setPage(0); }, [debouncedQuery, categoryId, statusFilter, stockFilter, sort]);
  const visible = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const active = serverProducts.filter(product => !isInactive(product));
  const withoutStock = active.filter(product => !(stock.get(String(idOf(product)))?.onHand > 0)).length;
  const toggleSort = key => setSort(current => ({ key, dir: current.key === key && current.dir === 'asc' ? 'desc' : 'asc' }));

  function exportCsv() {
    downloadCsv('inventary-productos-pagina-' + (page + 1) + '-' + isoDate() + '.csv', [
      { key: 'sku', label: 'SKU' }, { key: 'name', label: 'Producto' },
      { key: 'category', label: 'Categoría', csvValue: product => categoryNames.get(String(product.categoryId)) || '' },
      { key: 'salePrice', label: 'Precio de venta' },
      { key: 'onHand', label: 'Existencia', csvValue: product => stock.get(String(idOf(product)))?.onHand || 0 },
      { key: 'available', label: 'Disponible', csvValue: product => stock.get(String(idOf(product)))?.available || 0 },
      { key: 'active', label: 'Estado', csvValue: product => isInactive(product) ? 'Inactivo' : 'Activo' }
    ], rows);
  }

  return <>
    <div className="stat-chips">
      <span><strong>{number(serverTotal)}</strong> resultados</span>
      <span><strong>{number(active.length)}</strong> activos en esta página</span>
      <span className={withoutStock ? 'warn' : ''}><strong>{number(withoutStock)}</strong> activos sin stock en esta página</span>
    </div>
    <div className="filter-bar">
      <label className="search-field"><Search size={16} aria-hidden="true" /><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar por nombre o SKU" aria-label="Buscar productos" /></label>
      <select value={categoryId} onChange={event => setCategoryId(event.target.value)} aria-label="Categoría">
        <option value="">Todas las categorías</option>
        {categories.map(category => <option key={idOf(category)} value={idOf(category)}>{category.name}</option>)}
      </select>
      <select value={stockFilter} onChange={event => setStockFilter(event.target.value)} aria-label="Existencias">
        <option value="">Todas las existencias</option><option value="with">Con existencias</option><option value="without">Sin existencias</option>
      </select>
      <div className="segmented" role="group" aria-label="Estado">
        {[['active', 'Activos'], ['inactive', 'Inactivos'], ['', 'Todos']].map(([value, label]) => <button key={value || 'all'} type="button" aria-pressed={statusFilter === value} onClick={() => setStatusFilter(value)}>{label}</button>)}
      </div>
      <div className="filter-actions">
        <button type="button" className="button secondary" onClick={exportCsv} disabled={!rows.length}><Download size={15} />CSV</button>
        {can('catalog:manage') && <button type="button" className="button primary" onClick={onNew}><Plus size={16} />Nuevo producto</button>}
      </div>
    </div>
    {!ready || listLoading ? <SkeletonTable rows={8} columns={6} label="Cargando productos" /> : <div className="table-wrap data-table">
      <table aria-label="Productos">
        <thead><tr>
          <SortHeader id="name" label="Producto" sort={sort} onSort={toggleSort} />
          <SortHeader id="category" label="Categoría" sort={sort} onSort={toggleSort} />
          <SortHeader id="price" label="Precio" sort={sort} onSort={toggleSort} align="num" />
          <SortHeader id="stock" label="Existencia" sort={sort} onSort={toggleSort} align="num" />
          <th scope="col">Estado</th>
          {can('catalog:manage') && <th scope="col" className="actions-col"><span className="visually-hidden">Acciones</span></th>}
        </tr></thead>
        <tbody>{visible.map(product => {
          const id = String(idOf(product));
          const totals = stock.get(id);
          const inactive = isInactive(product);
          return <tr key={id} className="clickable" onClick={() => onOpenDetail(id)}>
            <td><button type="button" className="row-title" onClick={event => { event.stopPropagation(); onOpenDetail(id); }}><strong>{product.name}</strong><small>{product.sku}</small></button></td>
            <td>{categoryNames.get(String(product.categoryId)) || product.category?.name || '—'}</td>
            <td className="num">{money(product.salePrice)}</td>
            <td className="num"><span className={'stock-value' + (!(totals?.onHand > 0) && !inactive ? ' empty' : '')}>{number(totals?.onHand || 0)}</span>{totals && totals.available !== totals.onHand && <small className="cell-note">{number(totals.available)} disp.</small>}</td>
            <td><span className={'pill ' + (inactive ? 'inactive' : 'active')}>{inactive ? 'Inactivo' : 'Activo'}</span></td>
            {can('catalog:manage') && <td className="actions-col" onClick={event => event.stopPropagation()}>
              <div className="icon-actions">
                <button type="button" className="icon-button ghost" aria-label={'Editar ' + product.name} title="Editar" onClick={() => onEdit(product)}><Pencil size={15} /></button>
                <button type="button" className="icon-button ghost" aria-label={(inactive ? 'Activar ' : 'Desactivar ') + product.name} title={inactive ? 'Activar' : 'Desactivar'} onClick={() => onToggle(product)}><Power size={15} /></button>
                {inactive && <button type="button" className="icon-button ghost danger" aria-label={'Eliminar ' + product.name} title="Eliminar" onClick={() => onDelete(product)}><Trash2 size={15} /></button>}
              </div>
            </td>}
          </tr>;
        })}</tbody>
      </table>
      {listError && <div className="alert error" role="alert">{listError}</div>}
      {!rows.length && !listError && <div className="empty-block"><PackageSearch size={28} /><strong>Sin resultados</strong><span>{serverTotal ? 'Ajusta la búsqueda o los filtros.' : 'Todavía no hay productos.'}</span></div>}
      <Pagination page={page} total={serverTotal} onPage={setPage} />
    </div>}
  </>;
}

function CategoriesTab({ categories, products, ready, can, onNew, onEdit, onToggle, onDelete }) {
  const [query, setQuery] = useState('');
  const counts = useMemo(() => {
    const totals = new Map();
    products.forEach(product => totals.set(String(product.categoryId), (totals.get(String(product.categoryId)) || 0) + 1));
    return totals;
  }, [products]);
  const rows = categories.filter(category => !query.trim() || [category.name, category.code, category.description].some(value => String(value || '').toLowerCase().includes(query.trim().toLowerCase())));
  return <>
    <div className="filter-bar">
      <label className="search-field"><Search size={16} aria-hidden="true" /><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar categoría" aria-label="Buscar categorías" /></label>
      <div className="filter-actions">{can('catalog:manage') && <button type="button" className="button primary" onClick={onNew}><Plus size={16} />Nueva categoría</button>}</div>
    </div>
    {!ready ? <SkeletonTable rows={6} columns={4} label="Cargando categorías" /> : <div className="table-wrap data-table">
      <table aria-label="Categorías">
        <thead><tr><th scope="col">Categoría</th><th scope="col">Descripción</th><th scope="col" className="num">Productos</th><th scope="col">Estado</th>{can('catalog:manage') && <th scope="col" className="actions-col"><span className="visually-hidden">Acciones</span></th>}</tr></thead>
        <tbody>{rows.map(category => {
          const id = String(idOf(category));
          const inactive = isInactive(category);
          return <tr key={id}>
            <td><span className="row-title static"><strong>{category.name}</strong>{category.code && <small>{category.code}</small>}</span></td>
            <td className="muted-cell">{category.description || '—'}</td>
            <td className="num">{number(category.productCount ?? counts.get(id) ?? 0)}</td>
            <td><span className={'pill ' + (inactive ? 'inactive' : 'active')}>{inactive ? 'Inactiva' : 'Activa'}</span></td>
            {can('catalog:manage') && <td className="actions-col"><div className="icon-actions">
              <button type="button" className="icon-button ghost" aria-label={'Editar ' + category.name} title="Editar" onClick={() => onEdit(category)}><Pencil size={15} /></button>
              <button type="button" className="icon-button ghost" aria-label={(inactive ? 'Activar ' : 'Desactivar ') + category.name} title={inactive ? 'Activar' : 'Desactivar'} onClick={() => onToggle(category)}><Power size={15} /></button>
              {inactive && <button type="button" className="icon-button ghost danger" aria-label={'Eliminar ' + category.name} title="Eliminar" onClick={() => onDelete(category)}><Trash2 size={15} /></button>}
            </div></td>}
          </tr>;
        })}</tbody>
      </table>
      {!rows.length && <div className="empty-block"><Tags size={28} /><strong>Sin categorías</strong><span>{categories.length ? 'Ajusta la búsqueda.' : 'Crea la primera categoría.'}</span></div>}
    </div>}
  </>;
}

export default function ProductsPage({ tab, products = [], categories, balances, ready, refreshing, can, detailId, onNavigateTab, onOpenDetail, onCloseDetail, product: actions, category: categoryActions }) {
  const [detail, setDetail] = useState(null);
  useEffect(() => {
    if (!detailId) { setDetail(null); return undefined; }
    let active = true;
    setDetail(null);
    api.product(detailId).then(value => { if (active) setDetail(value); })
      .catch(error => { if (active) toast.error(error?.message || 'No se pudo cargar el detalle del producto.'); });
    return () => { active = false; };
  }, [detailId]);
  const categoryName = id => categories.find(category => String(idOf(category)) === String(id))?.name || '';
  return <div className={'page' + (refreshing ? ' is-refreshing' : '')}>
    <CatalogTabs active={tab} productCount={products.length} categoryCount={categories.length} onNavigate={onNavigateTab} />
    {tab === 'categories'
      ? <CategoriesTab categories={categories} products={products} ready={ready} can={can} {...categoryActions} />
      : <ProductsTab products={products} categories={categories} balances={balances} ready={ready} can={can} onOpenDetail={onOpenDetail} {...actions} />}
    {detail && <ProductDrawer product={detail} category={categoryName(detail.categoryId)} balances={balances} can={can} onClose={onCloseDetail}
      onEdit={actions.onEdit} onToggle={actions.onToggle} onDelete={actions.onDelete} />}
  </div>;
}

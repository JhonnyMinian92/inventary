import { ShoppingBag } from 'lucide-react';

function idOf(item) { return item?.id ?? item?.productId ?? item?.categoryId ?? ''; }
function titleCase(value = '') { return String(value).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }

export function ProductsTable({ products = [], categories = [], onEdit, onToggleActive }) {
  const categoryName = id => categories.find(category => String(idOf(category)) === String(id))?.name || '—';
  return <div className="table-wrap">
    <table aria-label="Catálogo de productos">
      <caption className="visually-hidden">Productos registrados y su estado</caption>
      <thead><tr><th scope="col">Producto</th><th scope="col">SKU</th><th scope="col">Categoría</th><th scope="col">Tipo</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead>
      <tbody>{products.map((product, index) => {
        const inactive = product.active === false || String(product.status || '').toUpperCase() === 'INACTIVE';
        return <tr key={idOf(product) || index}>
          <td><div className="product-cell"><span className="product-avatar"><ShoppingBag size={17}/></span><div><strong>{product.name || 'Sin nombre'}</strong><small>{product.description || 'Sin descripción'}</small></div></div></td>
          <td><code>{product.sku || product.code || '—'}</code></td>
          <td>{product.category?.name || categoryName(product.categoryId)}</td>
          <td>{titleCase(product.kind || 'STOCKED_GOOD')}</td>
          <td><span className={'pill ' + (inactive ? 'inactive' : 'active')}>{inactive ? 'Inactivo' : 'Activo'}</span></td>
          <td><div className="row-actions"><button type="button" className="small-action" aria-label={'Editar producto ' + (product.name || product.sku)} onClick={() => onEdit(product)}>Editar</button><button type="button" className="small-action" aria-label={(inactive ? 'Activar' : 'Desactivar') + ' producto ' + (product.name || product.sku)} onClick={() => onToggleActive(product)}>{inactive ? 'Activar' : 'Desactivar'}</button></div></td>
        </tr>;
      })}</tbody>
    </table>
    {!products.length && <div className="empty-state" role="status">No hay productos que coincidan con la búsqueda.</div>}
  </div>;
}

export function CategoriesTable({ categories = [], onEdit, onToggleActive }) {
  return <div className="table-wrap">
    <table aria-label="Catálogo de categorías">
      <caption className="visually-hidden">Categorías registradas y su estado</caption>
      <thead><tr><th scope="col">Categoría</th><th scope="col">Código</th><th scope="col">Descripción</th><th scope="col">Productos</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead>
      <tbody>{categories.map((category, index) => {
        const inactive = category.active === false || String(category.status || '').toUpperCase() === 'INACTIVE';
        return <tr key={idOf(category) || index}>
          <td><strong>{category.name || 'Sin nombre'}</strong></td><td><code>{category.code || '—'}</code></td><td>{category.description || '—'}</td><td>{category.productCount ?? '—'}</td>
          <td><span className={'pill ' + (inactive ? 'inactive' : 'active')}>{inactive ? 'Inactiva' : 'Activa'}</span></td>
          <td><div className="row-actions"><button type="button" className="small-action" aria-label={'Editar categoría ' + (category.name || category.code)} onClick={() => onEdit(category)}>Editar</button><button type="button" className="small-action" aria-label={(inactive ? 'Activar' : 'Desactivar') + ' categoría ' + (category.name || category.code)} onClick={() => onToggleActive(category)}>{inactive ? 'Activar' : 'Desactivar'}</button></div></td>
        </tr>;
      })}</tbody>
    </table>
    {!categories.length && <div className="empty-state" role="status">Todavía no has creado categorías.</div>}
  </div>;
}

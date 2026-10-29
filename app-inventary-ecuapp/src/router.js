// Rutas de la SPA. Todas cuelgan de la base pública de Vite (`base`, por defecto /inventary/), de modo que
// http://host/inventary/compras/123 es una URL real que nginx y el servidor de desarrollo devuelven con index.html.

export const DEFAULT_BASE = '/inventary/';
export const NEXT_PARAM = 'destino';
const RESERVED_SEGMENTS = new Set(['nuevo', 'nueva', 'editar', 'devoluciones', 'recepcion', 'categorias']);
const ID_PATTERN = /^[A-Za-z0-9._~-]{1,80}$/;

export function normalizeBase(value = DEFAULT_BASE) {
  return ('/' + String(value || '/').trim() + '/').replace(/\/{2,}/g, '/');
}

const APP_BASE = normalizeBase(import.meta.env?.BASE_URL ?? DEFAULT_BASE);

const ROUTE_DEFINITIONS = [
  { name: 'login', path: '/iniciar-sesion', title: 'Iniciar sesión' },
  { name: 'overview', path: '/resumen', section: 'overview', title: 'Resumen' },
  { name: 'products', path: '/productos', section: 'products', title: 'Productos' },
  { name: 'product-new', path: '/productos/nuevo', section: 'products', workflow: 'product', title: 'Nuevo producto' },
  { name: 'product-edit', path: '/productos/:id/editar', section: 'products', workflow: 'product', title: 'Editar producto' },
  { name: 'product-detail', path: '/productos/:id', section: 'products', detail: 'product', title: 'Detalle de producto' },
  // Las categorías son una pestaña de Productos.
  { name: 'categories', path: '/productos/categorias', section: 'categories', title: 'Categorías' },
  { name: 'category-new', path: '/productos/categorias/nueva', section: 'categories', workflow: 'category', title: 'Nueva categoría' },
  { name: 'category-edit', path: '/productos/categorias/:id/editar', section: 'categories', workflow: 'category', title: 'Editar categoría' },
  { name: 'categories-legacy', path: '/categorias', aliasOf: 'categories' },
  { name: 'category-new-legacy', path: '/categorias/nueva', aliasOf: 'category-new' },
  { name: 'category-edit-legacy', path: '/categorias/:id/editar', aliasOf: 'category-edit' },
  { name: 'partners', path: '/almacenes-y-contactos', section: 'partners', title: 'Almacenes y contactos' },
  { name: 'purchases', path: '/compras', section: 'purchases', title: 'Compras' },
  { name: 'purchase-new', path: '/compras/nueva', section: 'purchases', workflow: 'purchase', title: 'Nueva compra' },
  { name: 'purchase-return-new', path: '/compras/devoluciones/nueva', section: 'purchases', secondary: 'purchase-return', title: 'Nueva devolución a proveedor' },
  { name: 'purchase-detail', path: '/compras/:id', section: 'purchases', detail: 'purchase', title: 'Detalle de compra' },
  { name: 'purchase-receive', path: '/compras/:id/recepcion', section: 'purchases', workflow: 'receive', title: 'Registrar recepción' },
  { name: 'sales', path: '/ventas', section: 'sales', title: 'Ventas' },
  { name: 'sale-new', path: '/ventas/nueva', section: 'sales', workflow: 'sale', title: 'Nueva venta' },
  { name: 'sales-return-new', path: '/ventas/devoluciones/nueva', section: 'sales', secondary: 'sales-return', title: 'Nueva devolución de cliente' },
  { name: 'sale-detail', path: '/ventas/:id', section: 'sales', detail: 'sale', title: 'Detalle de venta' },
  { name: 'subscriptions', path: '/suscripciones', section: 'subscriptions', title: 'Suscripciones' },
  { name: 'subscription-detail', path: '/suscripciones/:id', section: 'subscriptions', detail: 'subscription', title: 'Detalle de suscripción' },
  { name: 'whatsapp', path: '/pedidos-whatsapp', section: 'whatsapp', title: 'Pedidos WhatsApp' },
  { name: 'whatsapp-detail', path: '/pedidos-whatsapp/:id', section: 'whatsapp', detail: 'whatsapp', title: 'Pedido WhatsApp' },
  { name: 'inventory', path: '/inventario', section: 'inventory', title: 'Inventario' },
  { name: 'inventory-transfer-new', path: '/inventario/traslados/nuevo', section: 'inventory', secondary: 'inventory-transfer', title: 'Nuevo traslado' },
  { name: 'inventory-count-new', path: '/inventario/conteos/nuevo', section: 'inventory', secondary: 'inventory-count', title: 'Nuevo conteo físico' },
  { name: 'inventory-adjustment-new', path: '/inventario/ajustes/nuevo', section: 'inventory', secondary: 'inventory-adjustment', title: 'Nuevo ajuste de stock' },
  { name: 'inventory-rule-new', path: '/inventario/reposicion/nueva', section: 'inventory', secondary: 'inventory-rule', title: 'Regla de reposición' },
  { name: 'reports', path: '/reportes', section: 'reports', title: 'Reportes' }
];

const ROUTES = ROUTE_DEFINITIONS.map(route => ({ ...route, segments: route.path.split('/').filter(Boolean) }));
const ROUTES_BY_NAME = new Map(ROUTES.map(route => [route.name, route]));

const WORKFLOW_ROUTES = { product: ['product-new', 'product-edit'], category: ['category-new', 'category-edit'], purchase: ['purchase-new'], sale: ['sale-new'], receive: [null, 'purchase-receive'] };
const SECONDARY_ROUTES = {
  'purchase-return': 'purchase-return-new',
  'sales-return': 'sales-return-new',
  'inventory-transfer': 'inventory-transfer-new',
  'inventory-count': 'inventory-count-new',
  'inventory-adjustment': 'inventory-adjustment-new',
  'inventory-rule': 'inventory-rule-new'
};
const DETAIL_ROUTES = { purchase: 'purchase-detail', sale: 'sale-detail', product: 'product-detail', whatsapp: 'whatsapp-detail', subscription: 'subscription-detail' };

export const HOME_ROUTE = 'overview';

export function appBase() { return APP_BASE; }

/** Ruta interna ('/compras/123') a partir del pathname del navegador, o null si queda fuera de la base. */
export function stripBase(pathname, base = APP_BASE) {
  const path = String(pathname || '/');
  if (base === '/') return path;
  const root = base.slice(0, -1);
  if (path === root) return '/';
  return path.startsWith(base) ? '/' + path.slice(base.length) : null;
}

function decodeSegment(segment) {
  try { return decodeURIComponent(segment); } catch { return null; }
}

export function pathFor(name, params = {}, base = APP_BASE) {
  const route = ROUTES_BY_NAME.get(name) || ROUTES_BY_NAME.get(HOME_ROUTE);
  const path = route.segments.map(segment => segment === ':id' ? encodeURIComponent(String(params.id ?? '')) : segment).join('/');
  return base + path;
}

export function matchRoute(appPath, base = APP_BASE) {
  if (typeof appPath !== 'string') return null;
  const segments = appPath.split('?')[0].split('/').filter(Boolean);
  let best = null;
  for (const route of ROUTES) {
    if (route.segments.length !== segments.length) continue;
    const params = {};
    let score = 0;
    const matches = route.segments.every((segment, index) => {
      if (segment !== ':id') {
        score += 1;
        return segment === segments[index];
      }
      const id = decodeSegment(segments[index]);
      if (!id || !ID_PATTERN.test(id) || RESERVED_SEGMENTS.has(id)) return false;
      params.id = id;
      return true;
    });
    if (matches && (!best || score > best.score)) best = { route, params, score };
  }
  if (!best) return null;
  const { params } = best;
  // Las rutas antiguas se resuelven a su ruta vigente; la aplicación reescribe la URL a la forma canónica.
  const route = best.route.aliasOf ? ROUTES_BY_NAME.get(best.route.aliasOf) : best.route;
  return {
    name: route.name,
    section: route.section || '',
    workflow: route.workflow || '',
    secondary: route.secondary || '',
    detail: route.detail || '',
    title: route.title,
    params,
    path: pathFor(route.name, params, base)
  };
}

export function currentRoute(location = globalThis.window?.location, base = APP_BASE) {
  if (!location) return null;
  const appPath = stripBase(location.pathname, base);
  return appPath === null ? null : matchRoute(appPath, base);
}

export function sectionPath(section, base = APP_BASE) {
  const route = ROUTES_BY_NAME.get(section);
  return pathFor(route?.section === section ? section : HOME_ROUTE, {}, base);
}

export function workflowPath(workflow, entityId = '', base = APP_BASE) {
  const [newRoute, entityRoute] = WORKFLOW_ROUTES[workflow] || [];
  if (entityId && entityRoute) return pathFor(entityRoute, { id: entityId }, base);
  return newRoute ? pathFor(newRoute, {}, base) : pathFor(HOME_ROUTE, {}, base);
}

export function secondaryPath(secondary, base = APP_BASE) {
  return pathFor(SECONDARY_ROUTES[secondary] || HOME_ROUTE, {}, base);
}

export function detailPath(kind, id, base = APP_BASE) {
  return DETAIL_ROUTES[kind] && id ? pathFor(DETAIL_ROUTES[kind], { id }, base) : pathFor(HOME_ROUTE, {}, base);
}

/** Solo admite rutas internas de la aplicación (evita redirecciones abiertas tras el login). */
export function safeRedirectPath(value, base = APP_BASE) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '';
  let url;
  try { url = new URL(value, 'http://inventary.invalid'); } catch { return ''; }
  if (url.origin !== 'http://inventary.invalid') return '';
  const appPath = stripBase(url.pathname, base);
  const route = appPath === null ? null : matchRoute(appPath, base);
  return route && route.name !== 'login' ? route.path : '';
}

export function loginPath(next = '', base = APP_BASE) {
  const target = safeRedirectPath(next, base);
  const login = pathFor('login', {}, base);
  return target && target !== pathFor(HOME_ROUTE, {}, base) ? `${login}?${NEXT_PARAM}=${encodeURIComponent(target)}` : login;
}

export function nextFromSearch(search, base = APP_BASE) {
  return safeRedirectPath(new URLSearchParams(search || '').get(NEXT_PARAM) || '', base);
}

/** Convierte las URLs anteriores basadas en hash (#/products/new) en rutas reales. */
export function legacyHashPath(hash, base = APP_BASE) {
  if (typeof hash !== 'string' || !hash.startsWith('#/')) return '';
  const [section, first, second] = hash.slice(2).split('?')[0].split('/').filter(Boolean).map(part => decodeSegment(part) ?? '');
  const id = first && ID_PATTERN.test(first) ? first : '';
  const go = (name, params) => pathFor(name, params, base);
  if (section === 'products' || section === 'categories') {
    const singular = section === 'products' ? 'product' : 'category';
    if (first === 'new') return go(`${singular}-new`);
    if (second === 'edit' && id) return go(`${singular}-edit`, { id });
  }
  if (section === 'purchases') {
    if (first === 'new') return go('purchase-new');
    if (first === 'returns' && second === 'new') return go('purchase-return-new');
    if (second === 'receive' && id) return go('purchase-receive', { id });
  }
  if (section === 'sales') {
    if (first === 'new') return go('sale-new');
    if (first === 'returns' && second === 'new') return go('sales-return-new');
  }
  if (section === 'inventory' && second === 'new') {
    const kind = { transfer: 'transfer', count: 'count', adjustment: 'adjustment', reorder: 'rule' }[first];
    if (kind) return go(`inventory-${kind}-new`);
  }
  const route = ROUTES_BY_NAME.get(section);
  return go(route?.section === section ? section : HOME_ROUTE);
}

export function migrateLegacyUrl() {
  const target = legacyHashPath(window.location.hash);
  if (target) window.history.replaceState(null, '', target);
}

export function navigate(path, { replace = false, state = null } = {}) {
  const current = window.location.pathname + window.location.search;
  if (current === path && !replace) return false;
  window.history[replace ? 'replaceState' : 'pushState'](state, '', path);
  return true;
}

/** Deja al navegador los clics con modificadores (abrir en otra pestaña, etc.). */
export function isPlainLeftClick(event) {
  return !event.defaultPrevented && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
}

export function documentTitle(route) {
  return route?.title ? `${route.title} · Inventary` : 'Inventary · Gestión de inventario';
}

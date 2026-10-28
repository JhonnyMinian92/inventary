import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  detailPath, legacyHashPath, loginPath, matchRoute, nextFromSearch, normalizeBase, pathFor,
  safeRedirectPath, secondaryPath, sectionPath, stripBase, workflowPath
} from './router.js';

const BASE = '/inventary/';

test('normaliza la base pública', () => {
  assert.equal(normalizeBase('inventary'), '/inventary/');
  assert.equal(normalizeBase('/inventary'), '/inventary/');
  assert.equal(normalizeBase('/'), '/');
});

test('separa la base del pathname y rechaza rutas externas', () => {
  assert.equal(stripBase('/inventary', BASE), '/');
  assert.equal(stripBase('/inventary/', BASE), '/');
  assert.equal(stripBase('/inventary/compras/42', BASE), '/compras/42');
  assert.equal(stripBase('/otra-app/compras', BASE), null);
  assert.equal(stripBase('/inventaryx/compras', BASE), null);
});

test('construye rutas bajo /inventary', () => {
  assert.equal(pathFor('overview', {}, BASE), '/inventary/resumen');
  assert.equal(sectionPath('partners', BASE), '/inventary/almacenes-y-contactos');
  assert.equal(sectionPath('desconocida', BASE), '/inventary/resumen');
  assert.equal(workflowPath('product', '', BASE), '/inventary/productos/nuevo');
  assert.equal(workflowPath('product', 'a b', BASE), '/inventary/productos/a%20b/editar');
  assert.equal(workflowPath('receive', '7f3c', BASE), '/inventary/compras/7f3c/recepcion');
  assert.equal(secondaryPath('inventory-rule', BASE), '/inventary/inventario/reposicion/nueva');
  assert.equal(detailPath('sale', 'abc', BASE), '/inventary/ventas/abc');
  assert.equal(detailPath('product', 'p-1', BASE), '/inventary/productos/p-1');
  assert.equal(detailPath('whatsapp', 'w-1', BASE), '/inventary/pedidos-whatsapp/w-1');
  assert.equal(sectionPath('categories', BASE), '/inventary/productos/categorias');
  assert.equal(workflowPath('category', 'c 1', BASE), '/inventary/productos/categorias/c%201/editar');
});

test('categorías vive dentro de productos y conserva las URL anteriores', () => {
  assert.equal(matchRoute('/productos/categorias', BASE).name, 'categories');
  assert.equal(matchRoute('/productos/categorias/nueva', BASE).workflow, 'category');
  assert.equal(matchRoute('/productos/nuevo', BASE).name, 'product-new');
  assert.equal(matchRoute('/productos/abc', BASE).detail, 'product');
  assert.equal(matchRoute('/pedidos-whatsapp/abc', BASE).detail, 'whatsapp');
  const legacy = matchRoute('/categorias/c-9/editar', BASE);
  assert.equal(legacy.name, 'category-edit');
  assert.equal(legacy.path, '/inventary/productos/categorias/c-9/editar');
  assert.equal(matchRoute('/categorias', BASE).path, '/inventary/productos/categorias');
});

test('resuelve rutas estáticas antes que parámetros', () => {
  assert.equal(matchRoute('/compras/nueva', BASE).name, 'purchase-new');
  assert.equal(matchRoute('/compras/devoluciones/nueva', BASE).name, 'purchase-return-new');
  const detail = matchRoute('/compras/3fa85f64-5717-4562-b3fc-2c963f66afa6', BASE);
  assert.equal(detail.name, 'purchase-detail');
  assert.equal(detail.detail, 'purchase');
  assert.equal(detail.params.id, '3fa85f64-5717-4562-b3fc-2c963f66afa6');
  const receive = matchRoute('/compras/abc/recepcion/', BASE);
  assert.equal(receive.workflow, 'receive');
  assert.equal(receive.path, '/inventary/compras/abc/recepcion');
  assert.equal(matchRoute('/compras/devoluciones', BASE), null);
  assert.equal(matchRoute('/inventario/reposicion/nueva', BASE).secondary, 'inventory-rule');
  assert.equal(matchRoute('/', BASE), null);
  assert.equal(matchRoute('/no-existe', BASE), null);
});

test('solo permite redirecciones internas después del login', () => {
  assert.equal(safeRedirectPath('/inventary/ventas/nueva', BASE), '/inventary/ventas/nueva');
  assert.equal(safeRedirectPath('//evil.example/inventary/resumen', BASE), '');
  assert.equal(safeRedirectPath('/\\evil.example', BASE), '');
  assert.equal(safeRedirectPath('/\t/evil.example', BASE), '');
  assert.equal(safeRedirectPath('https://evil.example/inventary/resumen', BASE), '');
  assert.equal(safeRedirectPath('/inventary/iniciar-sesion', BASE), '');
  assert.equal(safeRedirectPath('/inventary/no-existe', BASE), '');
  assert.equal(loginPath('/inventary/compras/9/recepcion', BASE), '/inventary/iniciar-sesion?destino=%2Finventary%2Fcompras%2F9%2Frecepcion');
  assert.equal(loginPath('/inventary/resumen', BASE), '/inventary/iniciar-sesion');
  assert.equal(nextFromSearch('?destino=%2Finventary%2Freportes', BASE), '/inventary/reportes');
  assert.equal(nextFromSearch('?destino=https%3A%2F%2Fevil.example', BASE), '');
});

test('migra las URLs antiguas con hash', () => {
  assert.equal(legacyHashPath('#/products/new', BASE), '/inventary/productos/nuevo');
  assert.equal(legacyHashPath('#/products/p-1/edit', BASE), '/inventary/productos/p-1/editar');
  assert.equal(legacyHashPath('#/purchases/o-9/receive', BASE), '/inventary/compras/o-9/recepcion');
  assert.equal(legacyHashPath('#/sales/returns/new', BASE), '/inventary/ventas/devoluciones/nueva');
  assert.equal(legacyHashPath('#/inventory/reorder/new', BASE), '/inventary/inventario/reposicion/nueva');
  assert.equal(legacyHashPath('#/reports', BASE), '/inventary/reportes');
  assert.equal(legacyHashPath('#/desconocido', BASE), '/inventary/resumen');
  assert.equal(legacyHashPath('#seccion', BASE), '');
});

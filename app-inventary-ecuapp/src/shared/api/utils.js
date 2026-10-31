
import { API_BASE, ApiError } from './http/request.js';

export function resolveApiUrl(path) {
  if (!path) return '';
  if (path.startsWith('/api/')) {
    const apiRoot = new URL(API_BASE.replace(/\/v\d+$/, '') + '/', window.location.href);
    return new URL(path.slice('/api/'.length), apiRoot).toString();
  }
  const base = new URL(API_BASE, window.location.href);
  return new URL(path, base).toString();
}
export function listFrom(payload) {
  if (Array.isArray(payload)) return payload;
  for (const key of ['items', 'content', 'data', 'results', 'records']) {
    if (Array.isArray(payload?.[key])) return payload[key];
  }
  return [];
}
export function identifier(item) {
  return item?.id ?? item?.productId ?? item?.categoryId ?? item?.sku ?? '';
}
// Solo para referencias pequeñas. Las tablas deben consultar páginas y filtros en el servidor.
export async function listAllPages(fetchPage, params = {}, pageSize = 100) {
  const size = Math.max(1, Math.min(100, Number(params.size) || pageSize));
  const first = await fetchPage({ ...params, page: 0, size });
  if (Array.isArray(first) || !Number.isFinite(Number(first?.totalPages))) return listFrom(first);
  const totalPages = Math.max(1, Number(first.totalPages) || 1);
  if (totalPages > 1000) throw new ApiError('El catálogo supera los 100.000 registros; usa filtros más específicos para evitar cargar un volumen excesivo en el navegador.', 413);
  const all = [...listFrom(first)];
  for (let page = 1; page < totalPages; page++) {
    const response = await fetchPage({ ...params, page, size });
    all.push(...listFrom(response));
  }
  return all;
}

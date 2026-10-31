/** Lee un parámetro de la URL sin acoplar el llamador a window. */
export function readSearchValue(search, key, fallback = '') {
  const params = new URLSearchParams(String(search || '').replace(/^\?/, ''));
  const value = params.get(key);
  return value === null ? fallback : value;
}

/** Fusiona cambios de query string y conserva los parámetros que pertenecen a otras vistas. */
export function mergeSearchParams(search, updates = {}) {
  const params = new URLSearchParams(String(search || '').replace(/^\?/, ''));
  for (const [key, value] of Object.entries(updates)) {
    if (value === null || value === undefined || value === '') params.delete(key);
    else params.set(key, String(value));
  }
  const serialized = params.toString();
  return serialized ? '?' + serialized : '';
}

/** Actualiza filtros/paginación sin borrar la ruta, el hash ni parámetros de otros dominios. */
export function updateBrowserQuery(updates, { replace = true, targetWindow = globalThis.window } = {}) {
  if (!targetWindow?.location || !targetWindow?.history) return false;
  const location = targetWindow.location;
  const search = mergeSearchParams(location.search, updates);
  const next = location.pathname + search + (location.hash || '');
  const current = location.pathname + location.search + (location.hash || '');
  if (next === current) return false;
  targetWindow.history[replace ? 'replaceState' : 'pushState'](targetWindow.history.state, '', next);
  return true;
}

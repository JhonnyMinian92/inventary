/**
 * Permission checks used by UI visibility controls.
 * A missing profile must never grant an explicit permission (fail-closed).
 */
export function hasPermission(user, permission) {
  if (permission == null || permission === '') return true;
  return Boolean(user && Array.isArray(user.permissions) && user.permissions.includes(permission));
}

import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPermission } from './permissions.js';

test('explicit permissions are denied until the authenticated profile is loaded', () => {
  assert.equal(hasPermission(null, 'reports:financial'), false);
  assert.equal(hasPermission(undefined, 'inventory:manage'), false);
});

test('a user receives only permissions present in the server profile', () => {
  const user = { permissions: ['catalog:read', 'inventory:read'] };
  assert.equal(hasPermission(user, 'catalog:read'), true);
  assert.equal(hasPermission(user, 'inventory:manage'), false);
  assert.equal(hasPermission(user, 'security:manage'), false);
});

test('permission-free UI elements remain available without an explicit permission', () => {
  assert.equal(hasPermission(null, ''), true);
  assert.equal(hasPermission(null, undefined), true);
});

test('missing or malformed permission collections fail closed', () => {
  assert.equal(hasPermission({}, 'audit:read'), false);
  assert.equal(hasPermission({ permissions: 'audit:read' }, 'audit:read'), false);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeSearchParams, readSearchValue, updateBrowserQuery } from './url-state.js';

test('readSearchValue returns the provided fallback only for missing keys', () => {
  assert.equal(readSearchValue('?products.q=cafe&page=2', 'products.q'), 'cafe');
  assert.equal(readSearchValue('?page=2', 'missing', 'default'), 'default');
  assert.equal(readSearchValue('?empty=', 'empty', 'default'), '');
});

test('mergeSearchParams updates only the requested filters and removes empty values', () => {
  assert.equal(
    mergeSearchParams('?keep=1&products.q=old&products.page=3', {
      'products.q': 'café con leche', 'products.page': 0, 'products.stock': null
    }),
    '?keep=1&products.q=caf%C3%A9+con+leche&products.page=0'
  );
});

test('updateBrowserQuery preserves pathname/hash and supports history replacement', () => {
  const calls = [];
  const targetWindow = {
    location: { pathname: '/inventary/productos', search: '?keep=1', hash: '#list' },
    history: { state: { route: true }, pushState: (...args) => calls.push(['push', ...args]), replaceState: (...args) => calls.push(['replace', ...args]) }
  };
  assert.equal(updateBrowserQuery({ 'products.page': 2 }, { targetWindow, replace: false }), true);
  assert.equal(calls[0][0], 'push');
  assert.equal(calls[0][3], '/inventary/productos?keep=1&products.page=2#list');
});

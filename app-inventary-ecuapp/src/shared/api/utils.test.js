import test from 'node:test';
import assert from 'node:assert/strict';
import { listAllPages } from './utils.js';

test('listAllPages traverses nested page metadata returned by the API', async () => {
  const calls = [];
  const rows = [['p-1', 'p-2'], ['p-3', 'p-4'], ['p-5']];
  const result = await listAllPages(async ({ page, size }) => {
    calls.push({ page, size });
    return { content: rows[page], page: { number: page, size, totalElements: 5, totalPages: 3 } };
  }, {}, 2);
  assert.deepEqual(result, ['p-1', 'p-2', 'p-3', 'p-4', 'p-5']);
  assert.deepEqual(calls, [{ page: 0, size: 2 }, { page: 1, size: 2 }, { page: 2, size: 2 }]);
});

test('listAllPages enforces the defensive limit using nested page metadata', async () => {
  await assert.rejects(
    () => listAllPages(async () => ({ content: [], page: { totalPages: 1001 } })),
    error => error?.status === 413
  );
});

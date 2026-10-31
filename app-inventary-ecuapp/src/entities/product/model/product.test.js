import test from 'node:test';
import assert from 'node:assert/strict';
import { isStockedProduct, productId, productName, productQuantity } from './product.js';

test('productId accepts current API identifier aliases without losing zero-like IDs', () => {
  assert.equal(productId({ id: 12, productId: 13 }), 12);
  assert.equal(productId({ categoryId: 'cat-1' }), 'cat-1');
  assert.equal(productId({ warehouseId: 'wh-1' }), 'wh-1');
  assert.equal(productId({ purchaseOrderId: 'po-1' }), 'po-1');
  assert.equal(productId(null), '');
});

test('productName and productQuantity tolerate partial backend projections', () => {
  assert.equal(productName({ description: 'Servicio' }), 'Servicio');
  assert.equal(productQuantity({ available: 7 }), 7);
  assert.equal(productQuantity({ onHand: 0, quantity: 4 }), 0);
});

test('stocked product predicate preserves the existing default kind', () => {
  assert.equal(isStockedProduct({}), true);
  assert.equal(isStockedProduct({ kind: 'SERVICE' }), false);
});

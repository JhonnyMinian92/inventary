import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_PRODUCT_UNIT_ID, isStockedProduct, productId, productName, productQuantity, productWritePayload } from './product.js';

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


test('product write payload preserves fiscal fields, default unit and subscription configuration', () => {
  const payload = productWritePayload({
    name: '  Plan mensual  ', categoryId: 'cat-1', kind: 'SERVICE', manualPrice: '19.95',
    serviceBilling: 'SUBSCRIPTION', billingFrequency: 'MONTHLY', termMonths: '6',
    taxCode: 'IVA_0', taxTreatment: 'ZERO_RATED', attributes: { brand: 'EcuApp' }
  });
  assert.equal(payload.name, 'Plan mensual');
  assert.equal(payload.unitOfMeasureId, DEFAULT_PRODUCT_UNIT_ID);
  assert.equal(payload.taxCode, 'IVA_0');
  assert.equal(payload.taxTreatment, 'ZERO_RATED');
  assert.equal(payload.salePrice, 19.95);
  assert.equal(payload.termMonths, 6);
  assert.deepEqual(payload.attributes, { brand: 'EcuApp' });
});

test('stocked product payload does not send a manually editable sale price', () => {
  const payload = productWritePayload({
    name: 'Teclado', categoryId: 'cat-1', kind: 'STOCKED_GOOD',
    marginPercent: '30', taxCode: 'IVA_15', taxTreatment: 'TAXABLE'
  });
  assert.equal(payload.marginPercent, 30);
  assert.equal(payload.salePrice, undefined);
  assert.equal(payload.unitOfMeasureId, DEFAULT_PRODUCT_UNIT_ID);
});


test('product write payload preserves fiscal fields, default unit and subscription configuration', () => {
  const payload = productWritePayload({
    name: '  Plan mensual  ', categoryId: 'cat-1', kind: 'SERVICE', manualPrice: '19.95',
    serviceBilling: 'SUBSCRIPTION', billingFrequency: 'MONTHLY', termMonths: '6',
    taxCode: 'IVA_0', taxTreatment: 'ZERO_RATED', attributes: { brand: 'EcuApp' }
  });
  assert.equal(payload.name, 'Plan mensual');
  assert.equal(payload.unitOfMeasureId, DEFAULT_PRODUCT_UNIT_ID);
  assert.equal(payload.taxCode, 'IVA_0');
  assert.equal(payload.taxTreatment, 'ZERO_RATED');
  assert.equal(payload.salePrice, 19.95);
  assert.equal(payload.termMonths, 6);
  assert.deepEqual(payload.attributes, { brand: 'EcuApp' });
});

test('stocked product payload does not send a manually editable sale price', () => {
  const payload = productWritePayload({
    name: 'Teclado', categoryId: 'cat-1', kind: 'STOCKED_GOOD',
    marginPercent: '30', taxCode: 'IVA_15', taxTreatment: 'TAXABLE'
  });
  assert.equal(payload.marginPercent, 30);
  assert.equal(payload.salePrice, undefined);
  assert.equal(payload.unitOfMeasureId, DEFAULT_PRODUCT_UNIT_ID);
});

test('page stock filtering keeps API search matches and aggregates warehouse balances', () => {
  const product = { id: 'p1', name: 'Camiseta', sku: 'CAM-1' };
  const other = { id: 'p2', name: 'Pantalón', sku: 'PAN-1' };
  const rows = [product, other];
  const balances = [{ productId: 'p1', onHand: 2 }, { productId: 'p1', onHand: 3 }];
  assert.deepEqual(filterProductsByPageStock(rows, balances, ''), rows);
  assert.deepEqual(filterProductsByPageStock(rows, balances, 'with'), [product]);
  assert.deepEqual(filterProductsByPageStock(rows, balances, 'without'), [other]);
});

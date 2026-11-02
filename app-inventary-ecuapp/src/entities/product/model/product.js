/** Domain selectors. Entity identifiers retain their API names at the boundary. */
export function productId(product) {
  return product?.id ?? product?.productId ?? product?.categoryId ?? product?.warehouseId ??
    product?.purchaseOrderId ?? product?.saleId ?? product?.subscriptionId ??
    product?.orderId ?? product?.customerId ?? product?.supplierId ?? '';
}
export function productName(product) {
  return product?.name ?? product?.description ?? product?.sku ?? String(productId(product));
}
export function productQuantity(balance) {
  return balance?.onHand ?? balance?.quantity ?? balance?.available ?? balance?.stock ?? 0;
}
export function isStockedProduct(product) {
  return (product?.kind || 'STOCKED_GOOD') === 'STOCKED_GOOD';
}


export const DEFAULT_PRODUCT_UNIT_ID = '00000000-0000-0000-0000-000000000001';

/** Build the catalog write contract in one place so the UI cannot silently drop fiscal fields. */
export function productWritePayload(form) {
  const kind = form?.kind || 'STOCKED_GOOD';
  const payload = {
    name: String(form?.name || '').trim(),
    kind,
    categoryId: form?.categoryId || '',
    unitOfMeasureId: form?.unitOfMeasureId || DEFAULT_PRODUCT_UNIT_ID,
    marginPercent: Number(form?.marginPercent ?? 25),
    description: String(form?.description || '').trim(),
    taxCode: form?.taxCode || 'IVA_15',
    taxTreatment: form?.taxTreatment || 'TAXABLE'
  };
  if (form?.attributes && typeof form.attributes === 'object' && !Array.isArray(form.attributes)) {
    payload.attributes = form.attributes;
  }
  if (kind !== 'STOCKED_GOOD') payload.salePrice = Number(form?.manualPrice);
  if (kind === 'SERVICE') {
    payload.serviceBilling = form?.serviceBilling || 'ONE_TIME';
    if (payload.serviceBilling === 'SUBSCRIPTION') {
      payload.billingFrequency = form?.billingFrequency;
      payload.termMonths = Number(form?.termMonths);
    }
  }
  return payload;
}

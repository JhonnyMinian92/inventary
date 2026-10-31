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

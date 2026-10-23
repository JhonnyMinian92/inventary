package com.ecuapp.inventary.catalog.application;

import com.ecuapp.inventary.catalog.Product;

/** Maps persistence-backed product state to an application result. */
public final class ProductResultMapper {
    private ProductResultMapper() {}

    public static ProductResult from(Product product) {
        return new ProductResult(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getCostPrice(),
            product.getSalePrice(),
            product.getCurrency(),
            product.getImageUrl(),
            product.getKind(),
            product.getUnitOfMeasureId(),
            product.getAttributes(),
            product.getAttributeSchemaVersion(),
            product.isActive(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}

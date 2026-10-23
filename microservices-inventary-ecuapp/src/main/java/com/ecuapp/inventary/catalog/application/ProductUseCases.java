package com.ecuapp.inventary.catalog.application;

import com.ecuapp.inventary.catalog.ProductKind;

import java.util.UUID;

/** Inbound application port for product commands and queries. */
public interface ProductUseCases {
    PageResult<ProductResult> listProducts(String query, UUID categoryId, Boolean active, ProductKind kind, PageQuery page);
    ProductResult getProduct(UUID id);
    ProductResult createProduct(ProductCommand command, String actor);
    ProductResult updateProduct(UUID id, ProductCommand command, String actor);
    void deactivateProduct(UUID id, String actor);
    void activateProduct(UUID id, String actor);
}

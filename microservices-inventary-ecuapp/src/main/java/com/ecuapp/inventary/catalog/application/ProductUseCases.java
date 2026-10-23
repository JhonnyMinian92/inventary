package com.ecuapp.inventary.catalog.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Inbound application port for product commands and queries. */
public interface ProductUseCases {
    Page<ProductResult> listProducts(String query, Pageable pageable);
    ProductResult getProduct(UUID id);
    ProductResult createProduct(ProductCommand command, String actor);
    ProductResult updateProduct(UUID id, ProductCommand command, String actor);
    void deactivateProduct(UUID id, String actor);
}

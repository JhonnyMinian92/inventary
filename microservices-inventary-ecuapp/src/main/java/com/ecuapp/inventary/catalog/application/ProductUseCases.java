package com.ecuapp.inventary.catalog.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Inbound port for product commands and queries. */
public interface ProductUseCases {
    Page<ProductResult> list(String query, Pageable pageable);
    ProductResult get(UUID id);
    ProductResult create(ProductCommand command, String actor);
    ProductResult update(UUID id, ProductCommand command, String actor);
    void deactivate(UUID id, String actor);
}

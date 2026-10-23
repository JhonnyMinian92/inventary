package com.ecuapp.inventary.catalog.application.port.out;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductKind;
import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence contract for product use cases.
 * Product remains a JPA-backed transitional model; separating the aggregate is
 * documented as a follow-up migration rather than hidden by this port.
 */
public interface ProductPersistencePort {
    PageResult<Product> search(String query, UUID categoryId, Boolean active, ProductKind kind, PageQuery page);
    PageResult<Product> findActivePublic(String query, UUID categoryId, PageQuery page);
    Optional<Product> findById(UUID id);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    Product save(Product product);
}

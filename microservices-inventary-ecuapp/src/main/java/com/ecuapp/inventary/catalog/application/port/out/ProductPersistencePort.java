package com.ecuapp.inventary.catalog.application.port.out;

import com.ecuapp.inventary.catalog.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence contract for the product use cases.
 * Product remains a JPA-backed transitional model; isolating its mapping into a
 * separate domain aggregate is a follow-up migration, not hidden by this port.
 */
public interface ProductPersistencePort {
    Page<Product> findAll(Pageable pageable);
    Page<Product> searchByNameOrSku(String query, Pageable pageable);
    Page<Product> findActivePublic(Pageable pageable);
    Optional<Product> findById(UUID id);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    Product save(Product product);
}

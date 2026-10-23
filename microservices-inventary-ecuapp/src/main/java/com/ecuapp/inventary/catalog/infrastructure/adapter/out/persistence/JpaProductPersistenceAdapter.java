package com.ecuapp.inventary.catalog.infrastructure.adapter.out.persistence;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.catalog.application.port.out.ProductPersistencePort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA adapter implementing the product persistence port. */
@Component
public class JpaProductPersistenceAdapter implements ProductPersistencePort {
    private final ProductRepository repository;

    public JpaProductPersistenceAdapter(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public Page<Product> searchByNameOrSku(String query, Pageable pageable) {
        return repository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(query, query, pageable);
    }

    @Override
    public Page<Product> findActivePublic(Pageable pageable) {
        return repository.findByActiveTrueAndCategory_ActiveTrue(pageable);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return repository.findBySkuIgnoreCase(sku);
    }

    @Override
    public boolean existsBySku(String sku) {
        return repository.existsBySku(sku);
    }

    @Override
    public Product save(Product product) {
        return repository.save(product);
    }
}

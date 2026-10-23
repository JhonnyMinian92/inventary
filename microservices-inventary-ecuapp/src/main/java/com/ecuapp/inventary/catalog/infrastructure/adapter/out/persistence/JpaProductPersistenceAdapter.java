package com.ecuapp.inventary.catalog.infrastructure.adapter.out.persistence;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;
import com.ecuapp.inventary.catalog.application.port.out.ProductPersistencePort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public PageResult<Product> findAll(PageQuery query) {
        return result(repository.findAll(pageable(query)));
    }

    @Override
    public PageResult<Product> searchByNameOrSku(String query, PageQuery page) {
        return result(repository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(query, query, pageable(page)));
    }

    @Override
    public PageResult<Product> findActivePublic(PageQuery page) {
        return result(repository.findByActiveTrueAndCategory_ActiveTrue(pageable(page)));
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

    private Pageable pageable(PageQuery query) {
        Sort sort = query.sort().isEmpty() ? Sort.unsorted() : Sort.by(query.sort().stream()
            .map(term -> new Sort.Order(term.direction() == PageQuery.Direction.DESC ? Sort.Direction.DESC : Sort.Direction.ASC, term.property()))
            .toList());
        return PageRequest.of(query.page(), Math.min(query.size(), 100), sort);
    }

    private PageResult<Product> result(Page<Product> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

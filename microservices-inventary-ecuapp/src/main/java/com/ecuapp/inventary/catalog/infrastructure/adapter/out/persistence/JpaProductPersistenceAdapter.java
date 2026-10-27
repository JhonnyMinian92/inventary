package com.ecuapp.inventary.catalog.infrastructure.adapter.out.persistence;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductKind;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;
import com.ecuapp.inventary.catalog.application.port.out.ProductPersistencePort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA adapter implementing the product persistence port. Sort properties are allowlisted upstream. */
@Component
public class JpaProductPersistenceAdapter implements ProductPersistencePort {
    private final ProductRepository repository;

    public JpaProductPersistenceAdapter(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<Product> search(String query, UUID categoryId, Boolean active, ProductKind kind, PageQuery page) {
        return result(repository.search(pattern(query), categoryId, active, kind, pageable(page)));
    }

    @Override
    public PageResult<Product> findActivePublic(String query, UUID categoryId, PageQuery page) {
        return result(repository.searchPublic(pattern(query), categoryId, pageable(page)));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Product> findForUpdate(UUID id) {
        return repository.findForUpdate(id);
    }

    @Override
    public void deleteAndFlush(Product product) {
        repository.deleteAndFlush(product);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return repository.findBySkuIgnoreCase(sku);
    }

    @Override
    public boolean existsBySku(String sku) {
        return repository.existsBySkuIgnoreCase(sku);
    }

    @Override
    public Product save(Product product) {
        return repository.save(product);
    }

    /** Builds a case-insensitive LIKE pattern escaping wildcard characters supplied by the client. */
    private String pattern(String query) {
        if (query == null || query.isBlank()) return null;
        String trimmed = query.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() > 100) trimmed = trimmed.substring(0, 100);
        return "%" + trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private Pageable pageable(PageQuery query) {
        Sort sort = query.sort().isEmpty() ? Sort.by("name") : Sort.by(query.sort().stream()
            .map(term -> new Sort.Order(term.direction() == PageQuery.Direction.DESC ? Sort.Direction.DESC : Sort.Direction.ASC, term.property()))
            .toList());
        return PageRequest.of(query.page(), Math.min(query.size(), 100), sort);
    }

    private PageResult<Product> result(Page<Product> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

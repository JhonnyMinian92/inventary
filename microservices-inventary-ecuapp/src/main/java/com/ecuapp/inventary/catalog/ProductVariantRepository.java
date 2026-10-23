package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant,UUID> {
 Optional<ProductVariant> findBySkuIgnoreCase(String sku);
 boolean existsBySkuIgnoreCase(String sku);
 List<ProductVariant> findByProductIdAndActiveTrue(UUID productId);
}

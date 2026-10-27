package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant,UUID> {
 Optional<ProductVariant> findBySkuIgnoreCase(String sku);
 Optional<ProductVariant> findFirstBySkuIgnoreCaseAndActiveTrue(String sku);
 Optional<ProductVariant> findFirstByBarcodeAndActiveTrue(String barcode);
 boolean existsBySkuIgnoreCase(String sku);
 boolean existsByBarcode(String barcode);
 @EntityGraph(attributePaths={"unitOfMeasure","product"})
 List<ProductVariant> findByProductIdOrderBySkuAsc(UUID productId);
 @EntityGraph(attributePaths={"unitOfMeasure","product"})
 List<ProductVariant> findByProductIdAndActiveTrue(UUID productId);
 boolean existsByUnitOfMeasureIdAndActiveTrue(UUID unitOfMeasureId);
 boolean existsByProductId(UUID productId);
}

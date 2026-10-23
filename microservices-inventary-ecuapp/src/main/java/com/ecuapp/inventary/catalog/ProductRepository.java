package com.ecuapp.inventary.catalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ProductRepository extends JpaRepository<Product,UUID> {
 boolean existsBySku(String sku);
 Page<Product> findByActiveTrue(Pageable pageable);
 Page<Product> findByActiveTrueAndCategory_ActiveTrue(Pageable pageable);
 Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name,String sku,Pageable pageable);
}

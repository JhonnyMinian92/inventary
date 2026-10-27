package com.ecuapp.inventary.catalog;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface ProductRepository extends com.ecuapp.inventary.common.FlushableJpaRepository<Product,UUID> {
 boolean existsBySku(String sku);
 boolean existsBySkuIgnoreCase(String sku);
 Optional<Product> findBySkuIgnoreCase(String sku);
 boolean existsByUnitOfMeasureIdAndActiveTrue(UUID unitOfMeasureId);
 boolean existsByUnitOfMeasureId(UUID unitOfMeasureId);
 @Query("select case when count(p)>0 then true else false end from Product p where p.category.id=:categoryId and p.active=true")
 boolean existsActiveByCategoryId(@Param("categoryId") UUID categoryId);
 @Query("select case when count(p)>0 then true else false end from Product p where p.category.id=:categoryId")
 boolean existsByCategoryId(@Param("categoryId") UUID categoryId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Product p where p.id=:id")
 Optional<Product> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"category"})
 @Query("""
  select p from Product p
  where (:pattern is null or lower(p.name) like :pattern escape '\\' or lower(p.sku) like :pattern escape '\\')
  and (:categoryId is null or p.category.id=:categoryId)
  and (:active is null or p.active=:active)
  and (:kind is null or p.kind=:kind)
  """)
 Page<Product> search(@Param("pattern") String pattern,@Param("categoryId") UUID categoryId,@Param("active") Boolean active,@Param("kind") ProductKind kind,Pageable pageable);
 @EntityGraph(attributePaths={"category"})
 @Query("""
  select p from Product p
  where p.active=true and p.category.active=true
  and (:pattern is null or lower(p.name) like :pattern escape '\\' or lower(p.sku) like :pattern escape '\\')
  and (:categoryId is null or p.category.id=:categoryId)
  """)
 Page<Product> searchPublic(@Param("pattern") String pattern,@Param("categoryId") UUID categoryId,Pageable pageable);
}

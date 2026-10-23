package com.ecuapp.inventary.returns;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface SalesReturnRepository extends JpaRepository<SalesReturn,UUID>{
 Optional<SalesReturn> findByIdempotencyKey(String key);
 @EntityGraph(attributePaths={"lines","lines.product","lines.sourceSaleItem","sourceSale","warehouse"})
 @Query("select distinct r from SalesReturn r where r.id=:id")
 Optional<SalesReturn> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"sourceSale","warehouse"})
 @Query("""
  select r from SalesReturn r
  where (:sourceSaleId is null or r.sourceSale.id=:sourceSaleId)
  and (:warehouseId is null or r.warehouse.id=:warehouseId)
  and r.createdAt>=:fromTime
  and r.createdAt<:toTime
  """)
 org.springframework.data.domain.Page<SalesReturn> search(@Param("sourceSaleId") UUID sourceSaleId,@Param("warehouseId") UUID warehouseId,
  @Param("fromTime") java.time.Instant fromTime,@Param("toTime") java.time.Instant toTime,org.springframework.data.domain.Pageable pageable);
}

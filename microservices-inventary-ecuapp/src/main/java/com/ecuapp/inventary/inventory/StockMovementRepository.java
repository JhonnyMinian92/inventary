package com.ecuapp.inventary.inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface StockMovementRepository extends JpaRepository<StockMovement,UUID>{
 Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);
 boolean existsByProductId(UUID productId);
 boolean existsByWarehouseId(UUID warehouseId);
 Optional<StockMovement> findFirstByReferenceTypeAndReferenceIdAndSourceLineIdAndMovementType(String referenceType,UUID referenceId,UUID sourceLineId,MovementType movementType);
 @EntityGraph(attributePaths={"product","warehouse","fromLocation","toLocation","unitOfMeasure"})
 @Query("""
  select m from StockMovement m
  where (:productId is null or m.product.id=:productId)
  and (:warehouseId is null or m.warehouse.id=:warehouseId)
  and (:type is null or m.movementType=:type)
  and (:referenceType is null or m.referenceType=:referenceType)
  and (:referenceId is null or m.referenceId=:referenceId)
  and m.occurredAt>=:fromTime
  and m.occurredAt<:toTime
  """)
 Page<StockMovement> search(@Param("productId") UUID productId,@Param("warehouseId") UUID warehouseId,@Param("type") MovementType type,
  @Param("referenceType") String referenceType,@Param("referenceId") UUID referenceId,@Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,Pageable pageable);
}

package com.ecuapp.inventary.purchasing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt,UUID>{
 Optional<GoodsReceipt> findByIdempotencyKey(String key);
 @EntityGraph(attributePaths={"lines","lines.product","lines.purchaseOrderLine","purchaseOrder","purchaseOrder.supplier","warehouse"})
 @Query("select distinct r from GoodsReceipt r where r.id=:id")
 Optional<GoodsReceipt> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"purchaseOrder","purchaseOrder.supplier","warehouse"})
 @Query("""
  select r from GoodsReceipt r
  where (:purchaseOrderId is null or r.purchaseOrder.id=:purchaseOrderId)
  and (:warehouseId is null or r.warehouse.id=:warehouseId)
  and r.receivedAt>=:fromTime
  and r.receivedAt<:toTime
  """)
 Page<GoodsReceipt> search(@Param("purchaseOrderId") UUID purchaseOrderId,@Param("warehouseId") UUID warehouseId,
  @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,Pageable pageable);

 @EntityGraph(attributePaths={"purchaseOrder","purchaseOrder.supplier","warehouse"})
 @Query("""
  select r from GoodsReceipt r where (:purchaseOrderId is null or r.purchaseOrder.id=:purchaseOrderId)
   and (:warehouseId is null or r.warehouse.id=:warehouseId) and r.warehouse.id in :warehouseIds
   and r.receivedAt>=:fromTime and r.receivedAt<:toTime
  """)
 Page<GoodsReceipt> searchScoped(@Param("purchaseOrderId") UUID purchaseOrderId,@Param("warehouseId") UUID warehouseId,
  @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,
  @Param("warehouseIds") java.util.Collection<UUID> warehouseIds,Pageable pageable);
}

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
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn,UUID>{
 Optional<PurchaseReturn> findByIdempotencyKey(String key);
 @EntityGraph(attributePaths={"lines","lines.product","lines.sourceReceiptLine","purchaseOrder","goodsReceipt","warehouse"})
 @Query("select distinct r from PurchaseReturn r where r.id=:id")
 Optional<PurchaseReturn> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"purchaseOrder","goodsReceipt","warehouse"})
 @Query("""
  select r from PurchaseReturn r
  where (:goodsReceiptId is null or r.goodsReceipt.id=:goodsReceiptId)
  and (:warehouseId is null or r.warehouse.id=:warehouseId)
  and r.createdAt>=:fromTime
  and r.createdAt<:toTime
  """)
 Page<PurchaseReturn> search(@Param("goodsReceiptId") UUID goodsReceiptId,@Param("warehouseId") UUID warehouseId,
  @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,Pageable pageable);
}

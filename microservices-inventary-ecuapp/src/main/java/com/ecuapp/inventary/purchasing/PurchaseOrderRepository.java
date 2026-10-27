package com.ecuapp.inventary.purchasing;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from PurchaseOrder p where p.id=:id")
 Optional<PurchaseOrder> findForUpdate(@Param("id") UUID id);
 @Query("select case when count(p)>0 then true else false end from PurchaseOrder p where p.warehouse.id=:warehouseId and p.status in :statuses")
 boolean existsForWarehouseInStatuses(@Param("warehouseId") UUID warehouseId,@Param("statuses") Collection<PurchaseOrderStatus> statuses);
 @Query("select case when count(distinct p)>0 then true else false end from PurchaseOrder p join p.lines l where l.product.id=:productId and p.status in :statuses")
 boolean existsForProductInStatuses(@Param("productId") UUID productId,@Param("statuses") Collection<PurchaseOrderStatus> statuses);
 @EntityGraph(attributePaths={"lines","lines.product","supplier","warehouse"})
 @Query("select distinct p from PurchaseOrder p where p.id=:id")
 Optional<PurchaseOrder> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"supplier","warehouse"})
 @Query("""
  select p from PurchaseOrder p
  where (:status is null or p.status=:status)
  and (:supplierId is null or p.supplier.id=:supplierId)
  and (:warehouseId is null or p.warehouse.id=:warehouseId)
  and p.orderedAt>=:fromTime
  and p.orderedAt<:toTime
  """)
 Page<PurchaseOrder> search(@Param("status") PurchaseOrderStatus status,@Param("supplierId") UUID supplierId,@Param("warehouseId") UUID warehouseId,
  @Param("fromTime") java.time.Instant fromTime,@Param("toTime") java.time.Instant toTime,Pageable pageable);
}

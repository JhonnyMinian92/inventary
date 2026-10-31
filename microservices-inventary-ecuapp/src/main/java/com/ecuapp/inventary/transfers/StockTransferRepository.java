package com.ecuapp.inventary.transfers;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
public interface StockTransferRepository extends JpaRepository<StockTransfer,UUID>{
 Optional<StockTransfer> findByIdempotencyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select t from StockTransfer t where t.id=:id")
 Optional<StockTransfer> findForUpdate(@Param("id") UUID id);
 @Query("select case when count(t)>0 then true else false end from StockTransfer t where (t.sourceWarehouse.id=:warehouseId or t.destinationWarehouse.id=:warehouseId) and t.status in :statuses")
 boolean existsForWarehouseInStatuses(@Param("warehouseId") UUID warehouseId,@Param("statuses") Collection<TransferStatus> statuses);
 @Query("select case when count(distinct t)>0 then true else false end from StockTransfer t join t.lines l where l.product.id=:productId and t.status in :statuses")
 boolean existsForProductInStatuses(@Param("productId") UUID productId,@Param("statuses") Collection<TransferStatus> statuses);
 @EntityGraph(attributePaths={"sourceWarehouse","destinationWarehouse","lines","lines.product"})
 @Query("select distinct t from StockTransfer t where t.id=:id")
 Optional<StockTransfer> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"sourceWarehouse","destinationWarehouse"})
 @Query("""
  select t from StockTransfer t
  where (:status is null or t.status=:status)
  and (:sourceWarehouseId is null or t.sourceWarehouse.id=:sourceWarehouseId)
  and (:destinationWarehouseId is null or t.destinationWarehouse.id=:destinationWarehouseId)
  """)
 Page<StockTransfer> search(@Param("status") TransferStatus status,@Param("sourceWarehouseId") UUID sourceWarehouseId,
  @Param("destinationWarehouseId") UUID destinationWarehouseId,Pageable pageable);

 @EntityGraph(attributePaths={"sourceWarehouse","destinationWarehouse"})
 @Query("""
  select t from StockTransfer t where (:status is null or t.status=:status)
   and (:sourceWarehouseId is null or t.sourceWarehouse.id=:sourceWarehouseId)
   and (:destinationWarehouseId is null or t.destinationWarehouse.id=:destinationWarehouseId)
   and (t.sourceWarehouse.id in :warehouseIds or t.destinationWarehouse.id in :warehouseIds)
  """)
 Page<StockTransfer> searchScoped(@Param("status") TransferStatus status,@Param("sourceWarehouseId") UUID sourceWarehouseId,
  @Param("destinationWarehouseId") UUID destinationWarehouseId,@Param("warehouseIds") java.util.Collection<UUID> warehouseIds,Pageable pageable);
}

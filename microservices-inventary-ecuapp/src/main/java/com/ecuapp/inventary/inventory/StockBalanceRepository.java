package com.ecuapp.inventary.inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
public interface StockBalanceRepository extends JpaRepository<StockBalance,UUID> {
 @Modifying(flushAutomatically=true)
 @Query(value="INSERT INTO stock_balance (id,product_id,warehouse_id,location_id,on_hand,reserved,version,updated_at) VALUES (:id,:productId,:warehouseId,:locationId,0,0,0,CURRENT_TIMESTAMP) ON CONFLICT (product_id,warehouse_id) DO NOTHING",nativeQuery=true)
 int insertIfAbsent(@Param("id") UUID id,@Param("productId") UUID productId,@Param("warehouseId") UUID warehouseId,@Param("locationId") UUID locationId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select b from StockBalance b where b.product.id=:productId and b.warehouse.id=:warehouseId")
 Optional<StockBalance> findForUpdate(@Param("productId") UUID productId,@Param("warehouseId") UUID warehouseId);
 Optional<StockBalance> findByProductIdAndWarehouseId(UUID productId,UUID warehouseId);
 @Query("select distinct b.product.id from StockBalance b where b.product.id in :productIds and (b.onHand - b.reserved) > 0")
 Set<UUID> findProductIdsWithAvailableStock(@Param("productIds") Collection<UUID> productIds);
}

package com.ecuapp.inventary.inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
public interface StockBalanceRepository extends JpaRepository<StockBalance,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select b from StockBalance b where b.product.id=:productId and b.warehouse.id=:warehouseId")
 Optional<StockBalance> findForUpdate(@Param("productId") UUID productId,@Param("warehouseId") UUID warehouseId);
 Optional<StockBalance> findByProductIdAndWarehouseId(UUID productId,UUID warehouseId);
 @Query("select distinct b.product.id from StockBalance b where b.product.id in :productIds and (b.onHand - b.reserved) > 0")
 Set<UUID> findProductIdsWithAvailableStock(@Param("productIds") Collection<UUID> productIds);
}

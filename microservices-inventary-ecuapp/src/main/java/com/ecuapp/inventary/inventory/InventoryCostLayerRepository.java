package com.ecuapp.inventary.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface InventoryCostLayerRepository extends JpaRepository<InventoryCostLayer,UUID> {
 /** Open layers in FIFO order (received time, then id) locked for consumption. */
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select l from InventoryCostLayer l where l.product.id=:productId and l.location.id=:locationId and l.remainingQuantity>0 order by l.receivedAt asc, l.id asc")
 List<InventoryCostLayer> findOpenForUpdate(@Param("productId") UUID productId,@Param("locationId") UUID locationId);

 @Query("select l from InventoryCostLayer l where l.product.id=:productId and l.location.id=:locationId and l.remainingQuantity>0 order by l.receivedAt asc, l.id asc")
 List<InventoryCostLayer> findOpen(@Param("productId") UUID productId,@Param("locationId") UUID locationId);

 @Query("select l from InventoryCostLayer l where l.product.id=:productId and l.location.id=:locationId order by l.receivedAt desc, l.id desc limit 1")
 List<InventoryCostLayer> findLatest(@Param("productId") UUID productId,@Param("locationId") UUID locationId);
}

package com.ecuapp.inventary.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface InventoryCostLayerRepository extends JpaRepository<InventoryCostLayer,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 List<InventoryCostLayer> findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(UUID productId,UUID locationId,java.math.BigDecimal remainingQuantity);
}

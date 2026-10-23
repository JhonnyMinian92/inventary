package com.ecuapp.inventary.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface InventoryValuationEntryRepository extends JpaRepository<InventoryValuationEntry,UUID> {
 List<InventoryValuationEntry> findByStockMovementIdOrderByCreatedAtAsc(UUID stockMovementId);
 @Query("select coalesce(sum(e.quantity),0), coalesce(sum(e.totalCost),0) from InventoryValuationEntry e where e.stockMovement.id=:movementId")
 List<Object[]> totals(@Param("movementId") UUID movementId);
}

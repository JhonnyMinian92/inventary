package com.ecuapp.inventary.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InventoryValuationEntryRepository extends JpaRepository<InventoryValuationEntry,UUID> {
 List<InventoryValuationEntry> findByStockMovementIdOrderByCreatedAtAsc(UUID stockMovementId);
}

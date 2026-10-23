package com.ecuapp.inventary.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCostingConfigRepository extends JpaRepository<InventoryCostingConfig,UUID> {
 Optional<InventoryCostingConfig> findBySingletonKey(String singletonKey);
}

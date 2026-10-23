package com.ecuapp.inventary.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ReorderRuleRepository extends JpaRepository<ReorderRule,UUID>{
 List<ReorderRule> findByActiveTrue();
 boolean existsByProductIdAndWarehouseId(UUID productId,UUID warehouseId);
}

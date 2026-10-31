package com.ecuapp.inventary.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
public interface ReorderRuleRepository extends JpaRepository<ReorderRule,UUID>{
 List<ReorderRule> findByActiveTrue();
 boolean existsByProductIdAndWarehouseId(UUID productId,UUID warehouseId);

 @Query("select r from ReorderRule r where r.warehouse.id in :warehouseIds")
 List<ReorderRule> findByWarehouseIdIn(@Param("warehouseIds") java.util.Collection<UUID> warehouseIds);
 @Query("select r from ReorderRule r where r.active=true and r.warehouse.id in :warehouseIds")
 List<ReorderRule> findByActiveTrueAndWarehouseIdIn(@Param("warehouseIds") java.util.Collection<UUID> warehouseIds);
}

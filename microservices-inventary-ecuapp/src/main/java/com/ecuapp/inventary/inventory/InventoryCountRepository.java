package com.ecuapp.inventary.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {
    Optional<InventoryCount> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"warehouse"})
    @Query("select c from InventoryCount c where (:warehouseId is null or c.warehouse.id = :warehouseId) and (:status is null or c.status = :status)")
    org.springframework.data.domain.Page<InventoryCount> search(@Param("warehouseId") UUID warehouseId,
                                                                @Param("status") InventoryCountStatus status,
                                                                org.springframework.data.domain.Pageable pageable);

    @Query("select case when count(c)>0 then true else false end from InventoryCount c where c.warehouse.id=:warehouseId and c.status=:status")
    boolean existsForWarehouseWithStatus(@Param("warehouseId") UUID warehouseId,@Param("status") InventoryCountStatus status);

    @Query("select case when count(c)>0 then true else false end from InventoryCount c where c.warehouse.id=:warehouseId")
    boolean existsForWarehouse(@Param("warehouseId") UUID warehouseId);

    @Query("select case when count(distinct c)>0 then true else false end from InventoryCount c join c.lines l where l.product.id=:productId and c.status=:status")
    boolean existsForProductWithStatus(@Param("productId") UUID productId,@Param("status") InventoryCountStatus status);

    @EntityGraph(attributePaths = {"warehouse", "lines", "lines.product"})
    @Query("select distinct c from InventoryCount c where c.id = :id")
    Optional<InventoryCount> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InventoryCount c where c.id = :id")
    Optional<InventoryCount> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"warehouse"})
    @Query("select c from InventoryCount c where (:warehouseId is null or c.warehouse.id = :warehouseId) and (:status is null or c.status = :status) and c.warehouse.id in :warehouseIds")
    org.springframework.data.domain.Page<InventoryCount> searchScoped(@Param("warehouseId") UUID warehouseId,
        @Param("status") InventoryCountStatus status,@Param("warehouseIds") java.util.Collection<UUID> warehouseIds,
        org.springframework.data.domain.Pageable pageable);
}

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

    @Override
    @EntityGraph(attributePaths = {"warehouse", "lines", "lines.product"})
    org.springframework.data.domain.Page<InventoryCount> findAll(org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "lines", "lines.product"})
    @Query("select distinct c from InventoryCount c where c.id = :id")
    Optional<InventoryCount> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InventoryCount c where c.id = :id")
    Optional<InventoryCount> findForUpdate(@Param("id") UUID id);
}

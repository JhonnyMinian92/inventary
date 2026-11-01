package com.ecuapp.inventary.taxpayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WarehouseEstablishmentAssignmentRepository extends JpaRepository<WarehouseEstablishmentAssignment,UUID> {
    boolean existsByWarehouseIdAndEstablishmentIdAndRelationshipTypeAndValidToIsNull(UUID warehouseId,UUID establishmentId,WarehouseEstablishmentAssignmentType relationshipType);
    List<WarehouseEstablishmentAssignment> findByWarehouseIdOrderByValidFromDesc(UUID warehouseId);
    List<WarehouseEstablishmentAssignment> findByWarehouseIdAndValidToIsNull(UUID warehouseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WarehouseEstablishmentAssignment a where a.id=:id")
    Optional<WarehouseEstablishmentAssignment> findForUpdateById(@Param("id") UUID id);
}

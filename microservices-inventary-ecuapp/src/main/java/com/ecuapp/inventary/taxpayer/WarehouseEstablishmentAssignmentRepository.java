package com.ecuapp.inventary.taxpayer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WarehouseEstablishmentAssignmentRepository extends JpaRepository<WarehouseEstablishmentAssignment,UUID> {
    boolean existsByWarehouseIdAndEstablishmentIdAndRelationshipTypeAndValidToIsNull(UUID warehouseId,UUID establishmentId,WarehouseEstablishmentAssignmentType relationshipType);
    List<WarehouseEstablishmentAssignment> findByWarehouseIdOrderByValidFromDesc(UUID warehouseId);
    List<WarehouseEstablishmentAssignment> findByWarehouseIdAndValidToIsNull(UUID warehouseId);
}

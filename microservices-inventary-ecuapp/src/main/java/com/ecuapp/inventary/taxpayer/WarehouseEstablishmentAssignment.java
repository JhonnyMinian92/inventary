package com.ecuapp.inventary.taxpayer;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="warehouse_establishment_assignment")
public class WarehouseEstablishmentAssignment {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="establishment_id",nullable=false) private SriEstablishment establishment;
    @Enumerated(EnumType.STRING) @Column(name="relationship_type",nullable=false,length=32) private WarehouseEstablishmentAssignmentType relationshipType;
    @Column(name="valid_from",nullable=false) private Instant validFrom;
    @Column(name="valid_to") private Instant validTo;
    @Column(nullable=false) private int priority;
    @Column(length=80) private String responsible;
    @Column(name="created_by",nullable=false,length=80) private String createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Version @Column(nullable=false) private long version;
    protected WarehouseEstablishmentAssignment() {}
    public WarehouseEstablishmentAssignment(Warehouse warehouse,SriEstablishment establishment,
            WarehouseEstablishmentAssignmentType relationshipType,Instant validFrom,int priority,String responsible,String createdBy) {
        this.id=UUID.randomUUID(); this.warehouse=warehouse; this.establishment=establishment;
        this.relationshipType=relationshipType; this.validFrom=validFrom; this.priority=priority;
        this.responsible=responsible; this.createdBy=createdBy; this.createdAt=Instant.now();
    }
    public void close(Instant validTo) {
        if(this.validTo!=null) throw new IllegalStateException("La asignación ya está cerrada.");
        if(validTo==null||!validTo.isAfter(validFrom)) throw new IllegalArgumentException("La fecha final debe ser posterior al inicio.");
        this.validTo=validTo;
    }
    public UUID getId(){return id;} public Warehouse getWarehouse(){return warehouse;} public SriEstablishment getEstablishment(){return establishment;}
    public WarehouseEstablishmentAssignmentType getRelationshipType(){return relationshipType;} public Instant getValidFrom(){return validFrom;}
    public Instant getValidTo(){return validTo;} public int getPriority(){return priority;} public String getResponsible(){return responsible;}
}

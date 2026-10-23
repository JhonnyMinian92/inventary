package com.ecuapp.inventary.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_costing_config",uniqueConstraints=@UniqueConstraint(name="uk_inventory_costing_singleton",columnNames="singleton_key"))
public class InventoryCostingConfig {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="singleton_key",nullable=false,length=24) private String singletonKey="DEFAULT";
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private CostingMethod method;
 @Column(name="effective_from",nullable=false) private Instant effectiveFrom;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected InventoryCostingConfig(){}
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public String getSingletonKey(){return singletonKey;} public CostingMethod getMethod(){return method;} public Instant getEffectiveFrom(){return effectiveFrom;} public String getCreatedBy(){return createdBy;}
}

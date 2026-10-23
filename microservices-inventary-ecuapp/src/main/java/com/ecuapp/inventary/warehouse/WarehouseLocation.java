package com.ecuapp.inventary.warehouse;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="warehouse_location",uniqueConstraints=@UniqueConstraint(name="uk_warehouse_location_code",columnNames={"warehouse_id","code"}))
public class WarehouseLocation {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(nullable=false,length=40) private String code;
 @Column(nullable=false,length=120) private String name;
 @Enumerated(EnumType.STRING) @Column(name="location_type",nullable=false,length=24) private LocationType locationType;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected WarehouseLocation(){}
 public WarehouseLocation(Warehouse warehouse,String code,String name,LocationType locationType){this.warehouse=warehouse;this.code=code;this.name=name;this.locationType=locationType;this.active=true;}
 @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public Warehouse getWarehouse(){return warehouse;} public String getCode(){return code;} public String getName(){return name;} public LocationType getLocationType(){return locationType;} public boolean isActive(){return active;}
 public enum LocationType { STORAGE, TRANSIT, QUARANTINE, SCRAP, PICKING }
}

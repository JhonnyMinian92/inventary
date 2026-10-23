package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="unit_of_measure", uniqueConstraints=@UniqueConstraint(name="uk_unit_of_measure_code",columnNames="code"))
public class UnitOfMeasure {
 public static final UUID DEFAULT_UNIT_ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @Id private UUID id;
 @Column(nullable=false,length=24) private String code;
 @Column(nullable=false,length=100) private String name;
 @Column(nullable=false,length=32) private String dimension;
 @Column(name="decimal_scale",nullable=false) private short decimalScale;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected UnitOfMeasure(){}
 public UnitOfMeasure(UUID id,String code,String name,String dimension,short decimalScale){this.id=id;this.code=code;this.name=name;this.dimension=dimension;this.decimalScale=decimalScale;this.active=true;this.createdAt=Instant.now();}
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public void update(String name,short decimalScale,boolean active){this.name=name;this.decimalScale=decimalScale;this.active=active;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getDimension(){return dimension;} public short getDecimalScale(){return decimalScale;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;}
}

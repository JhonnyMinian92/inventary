package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="unit_of_measure", uniqueConstraints=@UniqueConstraint(name="uk_unit_of_measure_code",columnNames="code"))
public class UnitOfMeasure {
 @Id private UUID id;
 @Column(nullable=false,length=24) private String code;
 @Column(nullable=false,length=100) private String name;
 @Column(nullable=false,length=32) private String dimension;
 @Column(name="decimal_scale",nullable=false) private short decimalScale;
 @Column(nullable=false) private boolean active=true;
 protected UnitOfMeasure(){}
 public UnitOfMeasure(UUID id,String code,String name,String dimension,short decimalScale){this.id=id;this.code=code;this.name=name;this.dimension=dimension;this.decimalScale=decimalScale;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getDimension(){return dimension;} public short getDecimalScale(){return decimalScale;} public boolean isActive(){return active;}
}

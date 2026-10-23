package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="unit_conversion",uniqueConstraints=@UniqueConstraint(name="uk_unit_conversion_pair",columnNames={"from_unit_id","to_unit_id"}))
public class UnitConversion {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="from_unit_id",nullable=false) private UnitOfMeasure fromUnit;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="to_unit_id",nullable=false) private UnitOfMeasure toUnit;
 @Column(nullable=false,precision=20,scale=9) private BigDecimal factor;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected UnitConversion(){}
 public UnitConversion(UnitOfMeasure fromUnit,UnitOfMeasure toUnit,BigDecimal factor){if(fromUnit==null||toUnit==null||fromUnit.getDimension()==null||!fromUnit.getDimension().equals(toUnit.getDimension()))throw new IllegalArgumentException("Las unidades deben pertenecer a la misma dimensión.");if(factor==null||factor.signum()<=0)throw new IllegalArgumentException("El factor de conversión debe ser positivo.");this.fromUnit=fromUnit;this.toUnit=toUnit;this.factor=factor;}
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public UnitOfMeasure getFromUnit(){return fromUnit;} public UnitOfMeasure getToUnit(){return toUnit;} public BigDecimal getFactor(){return factor;}
}

package com.ecuapp.inventary.tax;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="tax_rate")
public class TaxRate {
 @Id private UUID id;
 @Column(nullable=false,length=24) private String code;
 @Column(nullable=false,length=160) private String name;
 @Column(nullable=false,precision=7,scale=4) private BigDecimal percentage;
 @Column(name="valid_from",nullable=false) private LocalDate validFrom;
 @Column(name="valid_until") private LocalDate validUntil;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected TaxRate(){}
 public TaxRate(String code,String name,BigDecimal percentage,LocalDate validFrom,LocalDate validUntil){
  this.id=UUID.randomUUID();this.code=code.trim().toUpperCase();this.name=name.trim();this.percentage=percentage;
  this.validFrom=validFrom;this.validUntil=validUntil;
 }
 @PrePersist void create(){createdAt=Instant.now();}
 public boolean appliesTo(LocalDate date){return active&&!date.isBefore(validFrom)&&(validUntil==null||!date.isAfter(validUntil));}
 public void update(String name,BigDecimal percentage,LocalDate validFrom,LocalDate validUntil){
  if(name==null||name.isBlank())throw new IllegalArgumentException("El nombre de la tarifa es obligatorio.");
  if(percentage==null||percentage.signum()<0)throw new IllegalArgumentException("El porcentaje de la tarifa no puede ser negativo.");
  if(validFrom==null)throw new IllegalArgumentException("La fecha inicial es obligatoria.");
  if(validUntil!=null&&validUntil.isBefore(validFrom))throw new IllegalArgumentException("La fecha final no puede preceder a la fecha inicial.");
  this.name=name.trim();this.percentage=percentage.setScale(4,java.math.RoundingMode.HALF_UP);this.validFrom=validFrom;this.validUntil=validUntil;
 }
 public void activate(){active=true;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public BigDecimal getPercentage(){return percentage;} public LocalDate getValidFrom(){return validFrom;} public LocalDate getValidUntil(){return validUntil;}
 public void deactivate(){active=false;} public boolean isActive(){return active;}
}

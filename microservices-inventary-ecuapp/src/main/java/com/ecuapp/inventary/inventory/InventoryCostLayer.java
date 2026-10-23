package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_cost_layer",indexes=@Index(name="ix_inventory_cost_layer_fifo",columnList="product_id,warehouse_location_id,received_at"))
public class InventoryCostLayer {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_location_id",nullable=false) private WarehouseLocation location;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="source_movement_id") private StockMovement sourceMovement;
 @Column(name="received_quantity",nullable=false,precision=14,scale=3) private BigDecimal receivedQuantity;
 @Column(name="remaining_quantity",nullable=false,precision=14,scale=3) private BigDecimal remainingQuantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=4) private BigDecimal unitCost;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected InventoryCostLayer(){}
 public InventoryCostLayer(Product product,WarehouseLocation location,StockMovement sourceMovement,
   BigDecimal receivedQuantity,BigDecimal remainingQuantity,BigDecimal unitCost,String currency,Instant receivedAt){
  if(product==null||location==null||receivedQuantity==null||receivedQuantity.signum()<=0
      ||remainingQuantity==null||remainingQuantity.signum()<0||remainingQuantity.compareTo(receivedQuantity)>0
      ||unitCost==null||unitCost.signum()<0||currency==null||!currency.matches("[A-Z]{3}"))
   throw new IllegalArgumentException("La capa de costo contiene datos inválidos.");
  this.product=product;this.location=location;this.sourceMovement=sourceMovement;
  this.receivedQuantity=receivedQuantity.setScale(3,RoundingMode.UNNECESSARY);
  this.remainingQuantity=remainingQuantity.setScale(3,RoundingMode.UNNECESSARY);
  this.unitCost=unitCost.setScale(4,RoundingMode.HALF_UP);this.currency=currency;
  this.receivedAt=receivedAt==null?Instant.now():receivedAt;
 }
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public void consume(BigDecimal quantity){
  if(quantity==null||quantity.signum()<=0||remainingQuantity.compareTo(quantity)<0)
   throw new IllegalArgumentException("La salida supera la cantidad remanente de la capa de costo.");
  remainingQuantity=remainingQuantity.subtract(quantity).setScale(3,RoundingMode.UNNECESSARY);
 }
 public void restore(BigDecimal quantity){
  if(quantity==null||quantity.signum()<=0||remainingQuantity.add(quantity).compareTo(receivedQuantity)>0)
   throw new IllegalArgumentException("La reversión supera la cantidad recibida de la capa de costo.");
  remainingQuantity=remainingQuantity.add(quantity).setScale(3,RoundingMode.UNNECESSARY);
 }
 public void revalue(BigDecimal newUnitCost){
  if(newUnitCost==null||newUnitCost.signum()<0)throw new IllegalArgumentException("El costo de capa no puede ser negativo.");
  unitCost=newUnitCost.setScale(4,RoundingMode.HALF_UP);
 }
 public UUID getId(){return id;} public Product getProduct(){return product;} public WarehouseLocation getLocation(){return location;} public StockMovement getSourceMovement(){return sourceMovement;} public BigDecimal getReceivedQuantity(){return receivedQuantity;} public BigDecimal getRemainingQuantity(){return remainingQuantity;} public BigDecimal getUnitCost(){return unitCost;} public String getCurrency(){return currency;} public Instant getReceivedAt(){return receivedAt;}
}

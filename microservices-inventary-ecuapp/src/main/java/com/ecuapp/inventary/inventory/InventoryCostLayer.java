package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_cost_layer")
public class InventoryCostLayer {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_location_id",nullable=false) private WarehouseLocation location;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_movement_id",nullable=false) private StockMovement sourceMovement;
 @Column(name="received_quantity",nullable=false,precision=14,scale=3) private BigDecimal receivedQuantity;
 @Column(name="remaining_quantity",nullable=false,precision=14,scale=3) private BigDecimal remainingQuantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=4) private BigDecimal unitCost;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected InventoryCostLayer(){}
 public InventoryCostLayer(Product product,WarehouseLocation location,StockMovement sourceMovement,BigDecimal quantity,BigDecimal unitCost,String currency){
  this.product=product;this.location=location;this.sourceMovement=sourceMovement;this.receivedQuantity=quantity;this.remainingQuantity=quantity;
  this.unitCost=unitCost;this.currency=currency;this.receivedAt=sourceMovement.getOccurredAt()==null?Instant.now():sourceMovement.getOccurredAt();
 }
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 /** Consumes up to the requested quantity and returns the amount actually taken from this layer. */
 BigDecimal consume(BigDecimal requested){
  BigDecimal taken=requested.min(remainingQuantity);
  remainingQuantity=remainingQuantity.subtract(taken).setScale(3);
  return taken.setScale(3);
 }
 public UUID getId(){return id;} public Product getProduct(){return product;} public WarehouseLocation getLocation(){return location;} public StockMovement getSourceMovement(){return sourceMovement;} public BigDecimal getReceivedQuantity(){return receivedQuantity;} public BigDecimal getRemainingQuantity(){return remainingQuantity;} public BigDecimal getUnitCost(){return unitCost;} public String getCurrency(){return currency;} public Instant getReceivedAt(){return receivedAt;}
}

package com.ecuapp.inventary.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_valuation_entry")
public class InventoryValuationEntry {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="stock_movement_id",nullable=false) private StockMovement stockMovement;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cost_layer_id") private InventoryCostLayer costLayer;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private CostingMethod method;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=4) private BigDecimal unitCost;
 @Column(name="total_cost",nullable=false,precision=18,scale=4) private BigDecimal totalCost;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected InventoryValuationEntry(){}
 public InventoryValuationEntry(StockMovement stockMovement,InventoryCostLayer costLayer,CostingMethod method,
   BigDecimal quantity,BigDecimal unitCost,String currency){
  if(stockMovement==null||method==null||quantity==null||quantity.signum()<=0||unitCost==null||unitCost.signum()<0)
   throw new IllegalArgumentException("La entrada de valoración requiere movimiento, método, cantidad y costo válidos.");
  this.stockMovement=stockMovement;this.costLayer=costLayer;this.method=method;
  this.quantity=quantity.setScale(3,java.math.RoundingMode.UNNECESSARY);
  this.unitCost=unitCost.setScale(4,java.math.RoundingMode.HALF_UP);
  this.totalCost=this.quantity.multiply(this.unitCost).setScale(4,java.math.RoundingMode.HALF_UP);
  this.currency=currency;
 }
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public StockMovement getStockMovement(){return stockMovement;} public InventoryCostLayer getCostLayer(){return costLayer;} public CostingMethod getMethod(){return method;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getTotalCost(){return totalCost;} public String getCurrency(){return currency;}
}

package com.ecuapp.inventary.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only valuation ledger: cost assigned to each stock movement (protected by a PostgreSQL trigger). */
@Entity
@Table(name="inventory_valuation_entry")
public class InventoryValuationEntry {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="stock_movement_id",nullable=false,updatable=false) private StockMovement stockMovement;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cost_layer_id",updatable=false) private InventoryCostLayer costLayer;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24,updatable=false) private CostingMethod method;
 @Column(nullable=false,precision=14,scale=3,updatable=false) private BigDecimal quantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=4,updatable=false) private BigDecimal unitCost;
 @Column(name="total_cost",nullable=false,precision=18,scale=4,updatable=false) private BigDecimal totalCost;
 @Column(nullable=false,length=3,updatable=false) private String currency;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected InventoryValuationEntry(){}
 public InventoryValuationEntry(StockMovement movement,InventoryCostLayer layer,CostingMethod method,BigDecimal quantity,BigDecimal unitCost,BigDecimal totalCost,String currency){
  this.stockMovement=movement;this.costLayer=layer;this.method=method;this.quantity=quantity;this.unitCost=unitCost;this.totalCost=totalCost;this.currency=currency;
 }
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public StockMovement getStockMovement(){return stockMovement;} public InventoryCostLayer getCostLayer(){return costLayer;} public CostingMethod getMethod(){return method;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getTotalCost(){return totalCost;} public String getCurrency(){return currency;} public Instant getCreatedAt(){return createdAt;}
}

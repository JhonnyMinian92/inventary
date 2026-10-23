package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="stock_movement",uniqueConstraints=@UniqueConstraint(name="uk_stock_movement_idempotency_key",columnNames="idempotency_key"))
public class StockMovement {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Enumerated(EnumType.STRING) @Column(name="movement_type",nullable=false,length=32) private MovementType movementType;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=2) private BigDecimal unitCost;
 @Column(name="reference_type",length=40) private String referenceType;
 @Column(name="reference_id") private UUID referenceId;
 @Column(name="idempotency_key",length=120) private String idempotencyKey;
 @Column(name="request_hash",length=64) private String requestHash;
 @Column(length=1000) private String notes;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 protected StockMovement(){}
 public StockMovement(Product product,Warehouse warehouse,MovementType type,BigDecimal quantity,BigDecimal unitCost,String referenceType,UUID referenceId,String key,String hash,String notes,String actor){
  this.product=product;this.warehouse=warehouse;this.movementType=type;this.quantity=quantity;this.unitCost=unitCost;this.referenceType=referenceType;this.referenceId=referenceId;this.idempotencyKey=key;this.requestHash=hash;this.notes=notes;this.createdBy=actor;
 }
 @PrePersist void onCreate(){if(occurredAt==null)occurredAt=Instant.now();}
 public UUID getId(){return id;} public Product getProduct(){return product;} public Warehouse getWarehouse(){return warehouse;}
 public MovementType getMovementType(){return movementType;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;}
 public String getReferenceType(){return referenceType;} public UUID getReferenceId(){return referenceId;} public String getIdempotencyKey(){return idempotencyKey;}
 public String getRequestHash(){return requestHash;} public String getNotes(){return notes;} public String getCreatedBy(){return createdBy;} public Instant getOccurredAt(){return occurredAt;}
}

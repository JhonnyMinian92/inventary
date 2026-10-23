package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.UnitOfMeasure;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
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
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="from_location_id") private WarehouseLocation fromLocation;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="to_location_id") private WarehouseLocation toLocation;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="unit_of_measure_id") private UnitOfMeasure unitOfMeasure;
 @Column(name="source_line_id") private UUID sourceLineId;
 @Column(name="correlation_id",length=80) private String correlationId;
 @Enumerated(EnumType.STRING) @Column(name="valuation_method",length=24) private CostingMethod valuationMethod;
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
 public void assignLocations(WarehouseLocation from,WarehouseLocation to,UnitOfMeasure unit,String correlationId,UUID sourceLineId,CostingMethod valuationMethod){this.fromLocation=from;this.toLocation=to;this.unitOfMeasure=unit;this.correlationId=correlationId;this.sourceLineId=sourceLineId;this.valuationMethod=valuationMethod;}
 public UUID getId(){return id;} public WarehouseLocation getFromLocation(){return fromLocation;} public WarehouseLocation getToLocation(){return toLocation;} public UnitOfMeasure getUnitOfMeasure(){return unitOfMeasure;} public UUID getSourceLineId(){return sourceLineId;} public String getCorrelationId(){return correlationId;} public CostingMethod getValuationMethod(){return valuationMethod;} public Product getProduct(){return product;} public Warehouse getWarehouse(){return warehouse;}
 public MovementType getMovementType(){return movementType;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;}
 public String getReferenceType(){return referenceType;} public UUID getReferenceId(){return referenceId;} public String getIdempotencyKey(){return idempotencyKey;}
 public String getRequestHash(){return requestHash;} public String getNotes(){return notes;} public String getCreatedBy(){return createdBy;} public Instant getOccurredAt(){return occurredAt;}
}

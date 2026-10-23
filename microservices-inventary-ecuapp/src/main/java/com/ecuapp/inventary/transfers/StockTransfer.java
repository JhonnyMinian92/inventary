package com.ecuapp.inventary.transfers;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="stock_transfer",uniqueConstraints={
 @UniqueConstraint(name="uk_stock_transfer_number",columnNames="transfer_number"),
 @UniqueConstraint(name="uk_stock_transfer_idempotency",columnNames="idempotency_key")})
public class StockTransfer {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="transfer_number",nullable=false,length=48) private String transferNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TransferStatus status=TransferStatus.DRAFT;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_warehouse_id",nullable=false) private Warehouse sourceWarehouse;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="destination_warehouse_id",nullable=false) private Warehouse destinationWarehouse;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="dispatched_at") private Instant dispatchedAt;
 @Column(name="completed_at") private Instant completedAt;
 @Column(name="cancelled_at") private Instant cancelledAt;
 @Column(name="cancelled_by",length=80) private String cancelledBy;
 @Column(name="cancellation_reason",length=1000) private String cancellationReason;
 @Column(name="reversed_at") private Instant reversedAt;
 @Column(name="reversed_by",length=80) private String reversedBy;
 @Column(name="reversal_reason",length=1000) private String reversalReason;
 @OneToMany(mappedBy="transfer",cascade=CascadeType.ALL,orphanRemoval=true) private List<StockTransferLine> lines=new ArrayList<>();
 protected StockTransfer(){}
 public StockTransfer(String number,Warehouse source,Warehouse destination,String key,String hash,String actor){
  this.transferNumber=number;this.status=TransferStatus.DRAFT;this.sourceWarehouse=source;this.destinationWarehouse=destination;
  this.idempotencyKey=key;this.requestHash=hash;this.createdBy=actor;
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void addLine(StockTransferLine line){lines.add(line);line.setTransfer(this);}
 public void dispatch(){if(status!=TransferStatus.DRAFT)throw new IllegalStateException("Solo se puede despachar una transferencia en borrador.");status=TransferStatus.IN_TRANSIT;dispatchedAt=Instant.now();}
 public void complete(){if(status!=TransferStatus.IN_TRANSIT)throw new IllegalStateException("La transferencia no está en tránsito.");status=TransferStatus.COMPLETED;completedAt=Instant.now();}
 public void cancel(String reason,String actor){if(status!=TransferStatus.DRAFT&&status!=TransferStatus.IN_TRANSIT)throw new IllegalStateException("Solo se puede cancelar una transferencia en borrador o en tránsito.");status=TransferStatus.CANCELLED;cancelledAt=Instant.now();cancelledBy=actor;cancellationReason=reason;}
 public void reverse(String reason,String actor){if(status!=TransferStatus.COMPLETED)throw new IllegalStateException("Solo se puede revertir una transferencia completada.");status=TransferStatus.REVERSED;reversedAt=Instant.now();reversedBy=actor;reversalReason=reason;}
 public UUID getId(){return id;} public String getTransferNumber(){return transferNumber;} public TransferStatus getStatus(){return status;}
 public Warehouse getSourceWarehouse(){return sourceWarehouse;} public Warehouse getDestinationWarehouse(){return destinationWarehouse;}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;} public String getCreatedBy(){return createdBy;}
 public Instant getCreatedAt(){return createdAt;} public Instant getDispatchedAt(){return dispatchedAt;} public Instant getCompletedAt(){return completedAt;}
 public Instant getCancelledAt(){return cancelledAt;} public String getCancelledBy(){return cancelledBy;} public String getCancellationReason(){return cancellationReason;}
 public Instant getReversedAt(){return reversedAt;} public String getReversedBy(){return reversedBy;} public String getReversalReason(){return reversalReason;}
 public List<StockTransferLine> getLines(){return lines;}
}

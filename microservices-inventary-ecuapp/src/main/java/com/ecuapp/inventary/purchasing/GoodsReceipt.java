package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="goods_receipt",uniqueConstraints={
 @UniqueConstraint(name="uk_goods_receipt_number",columnNames="receipt_number"),
 @UniqueConstraint(name="uk_goods_receipt_idempotency",columnNames="idempotency_key")})
public class GoodsReceipt {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="receipt_number",nullable=false,length=48) private String receiptNumber;
 @Column(nullable=false,length=16) private String status="POSTED";
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_order_id",nullable=false) private PurchaseOrder purchaseOrder;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="receipt",cascade=CascadeType.ALL,orphanRemoval=true) private List<GoodsReceiptLine> lines=new ArrayList<>();
 protected GoodsReceipt(){}
 public GoodsReceipt(String receiptNumber,PurchaseOrder order,Warehouse warehouse,String key,String hash,String actor){
  this.receiptNumber=receiptNumber;this.purchaseOrder=order;this.warehouse=warehouse;this.idempotencyKey=key;this.requestHash=hash;this.createdBy=actor;this.receivedAt=Instant.now();
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void addLine(GoodsReceiptLine line){lines.add(line);line.setReceipt(this);}
 public UUID getId(){return id;} public String getReceiptNumber(){return receiptNumber;} public String getStatus(){return status;}
 public PurchaseOrder getPurchaseOrder(){return purchaseOrder;} public Warehouse getWarehouse(){return warehouse;}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;}
 public String getCreatedBy(){return createdBy;} public Instant getReceivedAt(){return receivedAt;} public Instant getCreatedAt(){return createdAt;}
 public List<GoodsReceiptLine> getLines(){return lines;}
}

package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="purchase_return",uniqueConstraints={
 @UniqueConstraint(name="uk_purchase_return_number",columnNames="return_number"),
 @UniqueConstraint(name="uk_purchase_return_idempotency",columnNames="idempotency_key")})
public class PurchaseReturn {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="return_number",nullable=false,length=48) private String returnNumber;
 @Column(nullable=false,length=16) private String status="POSTED";
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_order_id",nullable=false) private PurchaseOrder purchaseOrder;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="goods_receipt_id",nullable=false) private GoodsReceipt goodsReceipt;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(nullable=false,length=1000) private String reason;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="purchaseReturn",cascade=CascadeType.ALL,orphanRemoval=true) private List<PurchaseReturnLine> lines=new ArrayList<>();
 protected PurchaseReturn(){}
 public PurchaseReturn(String number,PurchaseOrder order,GoodsReceipt receipt,Warehouse warehouse,String reason,String key,String hash,String actor){
  this.returnNumber=number;this.purchaseOrder=order;this.goodsReceipt=receipt;this.warehouse=warehouse;this.reason=reason;this.idempotencyKey=key;this.requestHash=hash;this.createdBy=actor;
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void addLine(PurchaseReturnLine line){lines.add(line);line.setPurchaseReturn(this);}
 public UUID getId(){return id;} public String getReturnNumber(){return returnNumber;} public String getStatus(){return status;}
 public PurchaseOrder getPurchaseOrder(){return purchaseOrder;} public GoodsReceipt getGoodsReceipt(){return goodsReceipt;} public Warehouse getWarehouse(){return warehouse;}
 public String getReason(){return reason;} public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;}
 public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public List<PurchaseReturnLine> getLines(){return lines;}
}

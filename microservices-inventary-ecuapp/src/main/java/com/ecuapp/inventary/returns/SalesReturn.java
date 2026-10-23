package com.ecuapp.inventary.returns;

import com.ecuapp.inventary.sales.Sale;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="sales_return",uniqueConstraints={
 @UniqueConstraint(name="uk_sales_return_number",columnNames="return_number"),
 @UniqueConstraint(name="uk_sales_return_idempotency",columnNames="idempotency_key")})
public class SalesReturn {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="return_number",nullable=false,length=48) private String returnNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private ReturnStatus status=ReturnStatus.POSTED;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_sale_id",nullable=false) private Sale sourceSale;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(nullable=false,length=1000) private String reason;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="salesReturn",cascade=CascadeType.ALL,orphanRemoval=true) private List<SalesReturnLine> lines=new ArrayList<>();
 protected SalesReturn(){}
 public SalesReturn(String number,Sale sourceSale,Warehouse warehouse,String reason,String key,String hash,String actor){
  this.returnNumber=number;this.status=ReturnStatus.POSTED;this.sourceSale=sourceSale;this.warehouse=warehouse;
  this.reason=reason;this.idempotencyKey=key;this.requestHash=hash;this.createdBy=actor;
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void addLine(SalesReturnLine line){lines.add(line);line.setSalesReturn(this);}
 public UUID getId(){return id;} public String getReturnNumber(){return returnNumber;} public ReturnStatus getStatus(){return status;}
 public Sale getSourceSale(){return sourceSale;} public Warehouse getWarehouse(){return warehouse;} public String getReason(){return reason;}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;}
 public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public List<SalesReturnLine> getLines(){return lines;}
}

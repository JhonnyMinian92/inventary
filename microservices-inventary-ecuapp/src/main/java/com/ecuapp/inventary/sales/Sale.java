package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.partners.BusinessPartner;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="sale",uniqueConstraints={
 @UniqueConstraint(name="uk_sale_number",columnNames="sale_number"),
 @UniqueConstraint(name="uk_sale_idempotency_key",columnNames="idempotency_key")})
public class Sale {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="sale_number",nullable=false,length=48) private String saleNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private SaleStatus status=SaleStatus.COMPLETED;
 @Column(name="customer_name",length=160) private String customerName;
 @Column(name="customer_phone",length=40) private String customerPhone;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private SaleChannel channel;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id") private BusinessPartner customer;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal tax;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="idempotency_key",length=120) private String idempotencyKey;
 @Column(name="request_hash",length=64) private String requestHash;
 @Column(name="created_by",nullable=false,length=80) private String createdBy;
 @Column(name="cancellation_reason",length=1000) private String cancellationReason;
 @Column(name="cancellation_requested_by",length=80) private String cancellationRequestedBy;
 @Column(name="cancellation_requested_at") private Instant cancellationRequestedAt;
 @Column(name="cancellation_approved_by",length=80) private String cancellationApprovedBy;
 @Column(name="cancellation_approved_at") private Instant cancellationApprovedAt;
 @Column(name="sold_at",nullable=false) private Instant soldAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="sale",cascade=CascadeType.ALL,orphanRemoval=true) private List<SaleItem> items=new ArrayList<>();
 protected Sale(){}
 public Sale(String saleNumber,String customerName,String customerPhone,SaleChannel channel,Warehouse warehouse,
  BigDecimal subtotal,BigDecimal tax,BigDecimal total,String currency,String idempotencyKey,String requestHash,String createdBy,BusinessPartner customer){
  this.saleNumber=saleNumber;this.status=SaleStatus.COMPLETED;this.customerName=customerName;this.customerPhone=customerPhone;
  this.channel=channel;this.warehouse=warehouse;this.customer=customer;this.subtotal=subtotal;this.tax=tax;this.total=total;this.currency=currency;
  this.idempotencyKey=idempotencyKey;this.requestHash=requestHash;this.createdBy=createdBy;this.soldAt=Instant.now();
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();if(soldAt==null)soldAt=Instant.now();}
 public void addItem(SaleItem item){items.add(item);item.setSale(this);}
 public void requestCancellation(String reason,String actor){if(status!=SaleStatus.COMPLETED)throw new IllegalStateException("Solo se puede solicitar la cancelación de una venta completada.");this.status=SaleStatus.CANCELLATION_REQUESTED;this.cancellationReason=reason;this.cancellationRequestedBy=actor;this.cancellationRequestedAt=Instant.now();}
 public void approveCancellation(String actor){if(status!=SaleStatus.CANCELLATION_REQUESTED)throw new IllegalStateException("La venta no tiene una cancelación pendiente.");if(actor!=null&&actor.equals(cancellationRequestedBy))throw new IllegalArgumentException("La aprobación requiere un actor distinto al solicitante.");this.status=SaleStatus.CANCELLED;this.cancellationApprovedBy=actor;this.cancellationApprovedAt=Instant.now();}
 public UUID getId(){return id;} public String getSaleNumber(){return saleNumber;} public SaleStatus getStatus(){return status;}
 public String getCustomerName(){return customerName;} public String getCustomerPhone(){return customerPhone;} public SaleChannel getChannel(){return channel;}
 public Warehouse getWarehouse(){return warehouse;} public BusinessPartner getCustomer(){return customer;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getTax(){return tax;}
 public BigDecimal getTotal(){return total;} public String getCurrency(){return currency;} public String getIdempotencyKey(){return idempotencyKey;}
 public String getRequestHash(){return requestHash;} public String getCreatedBy(){return createdBy;} public String getCancellationReason(){return cancellationReason;} public String getCancellationRequestedBy(){return cancellationRequestedBy;} public Instant getCancellationRequestedAt(){return cancellationRequestedAt;} public String getCancellationApprovedBy(){return cancellationApprovedBy;} public Instant getCancellationApprovedAt(){return cancellationApprovedAt;} public Instant getSoldAt(){return soldAt;}
 public Instant getCreatedAt(){return createdAt;} public List<SaleItem> getItems(){return items;}
}

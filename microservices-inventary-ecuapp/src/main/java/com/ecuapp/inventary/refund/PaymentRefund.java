package com.ecuapp.inventary.refund;

import com.ecuapp.inventary.returns.SalesReturn;
import com.ecuapp.inventary.sales.Sale;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payment_refund")
public class PaymentRefund {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="refund_number",nullable=false,unique=true,length=80) private String refundNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_sale_id") private Sale sale;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sales_return_id") private SalesReturn salesReturn;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private RefundStatus status=RefundStatus.REQUESTED;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="payment_provider",length=80) private String paymentProvider;
 @Column(name="provider_reference",length=160) private String providerReference;
 @Column(nullable=false,length=1000) private String reason;
 @Column(name="requested_by",nullable=false,length=80) private String requestedBy;
 @Column(name="approved_by",length=80) private String approvedBy;
 @Column(name="processed_at") private Instant processedAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Column(name="idempotency_key",nullable=false,length=120,unique=true) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 protected PaymentRefund(){}
 public PaymentRefund(String refundNumber,Sale sale,SalesReturn salesReturn,BigDecimal amount,String currency,String paymentProvider,String providerReference,String reason,String requestedBy,String idempotencyKey,String requestHash){
  this.refundNumber=refundNumber;this.sale=sale;this.salesReturn=salesReturn;this.amount=amount;this.currency=currency;
  this.paymentProvider=paymentProvider;this.providerReference=providerReference;this.reason=reason;this.requestedBy=requestedBy;
  this.idempotencyKey=idempotencyKey;this.requestHash=requestHash;
 }
 @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 public void approve(String actor){if(status!=RefundStatus.REQUESTED)throw new IllegalStateException("Solo se aprueba un reembolso solicitado.");status=RefundStatus.APPROVED;approvedBy=actor;updatedAt=Instant.now();}
 public void processing(){status=RefundStatus.PROCESSING;updatedAt=Instant.now();}
 public void complete(String providerReference){
  if(providerReference!=null&&!providerReference.isBlank())this.providerReference=providerReference.trim();
  status=RefundStatus.COMPLETED;processedAt=Instant.now();updatedAt=processedAt;
 }
 public void fail(){status=RefundStatus.FAILED;updatedAt=Instant.now();}
 public void cancel(String actor){
  if(status==RefundStatus.COMPLETED||status==RefundStatus.CANCELLED)throw new IllegalStateException("El reembolso ya está cerrado.");
  status=RefundStatus.CANCELLED;updatedAt=Instant.now();
 }
 public void setProviderReference(String providerReference){
  if(providerReference!=null&&!providerReference.isBlank())this.providerReference=providerReference.trim();
 }
 public UUID getId(){return id;} public String getRefundNumber(){return refundNumber;} public Sale getSale(){return sale;}
 public SalesReturn getSalesReturn(){return salesReturn;} public RefundStatus getStatus(){return status;} public BigDecimal getAmount(){return amount;}
 public String getCurrency(){return currency;} public String getPaymentProvider(){return paymentProvider;} public String getProviderReference(){return providerReference;}
 public String getReason(){return reason;} public String getRequestedBy(){return requestedBy;} public String getApprovedBy(){return approvedBy;}
}

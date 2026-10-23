package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="whatsapp_order",uniqueConstraints={
 @UniqueConstraint(name="uk_whatsapp_order_number",columnNames="order_number"),
 @UniqueConstraint(name="uk_whatsapp_order_quote",columnNames="quote_id"),
 @UniqueConstraint(name="uk_whatsapp_order_idempotency",columnNames="idempotency_key")})
public class WhatsAppOrder {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="order_number",nullable=false,length=48) private String orderNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cart_id",nullable=false) private WhatsAppCart cart;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="quote_id",nullable=false) private WhatsAppQuote quote;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(name="customer_ref",nullable=false,length=120) private String customerRef;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private WhatsAppOrderStatus status=WhatsAppOrderStatus.AWAITING_PAYMENT;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal tax;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) private List<WhatsAppOrderItem> items=new ArrayList<>();
 protected WhatsAppOrder(){}
 public WhatsAppOrder(String number,WhatsAppCart cart,WhatsAppQuote quote,Warehouse warehouse,String customerRef,String key,String hash,Instant expiresAt){
  this.orderNumber=number;this.cart=cart;this.quote=quote;this.warehouse=warehouse;this.customerRef=customerRef;this.status=WhatsAppOrderStatus.AWAITING_PAYMENT;
  this.currency=quote.getCurrency();this.subtotal=quote.getSubtotal();this.tax=quote.getTax();this.total=quote.getTotal();this.idempotencyKey=key;this.requestHash=hash;this.expiresAt=expiresAt;
 }
 @PrePersist void onCreate(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void addItem(WhatsAppOrderItem item){items.add(item);item.setOrder(this);}
 public void cancel(){status=WhatsAppOrderStatus.CANCELLED;}
 public void expire(){status=WhatsAppOrderStatus.EXPIRED;}
 public void confirm(){status=WhatsAppOrderStatus.CONFIRMED;}
 public UUID getId(){return id;} public String getOrderNumber(){return orderNumber;} public WhatsAppCart getCart(){return cart;} public WhatsAppQuote getQuote(){return quote;}
 public Warehouse getWarehouse(){return warehouse;} public String getCustomerRef(){return customerRef;} public WhatsAppOrderStatus getStatus(){return status;}
 public String getCurrency(){return currency;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getTax(){return tax;} public BigDecimal getTotal(){return total;}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;} public Instant getExpiresAt(){return expiresAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public List<WhatsAppOrderItem> getItems(){return items;}
}

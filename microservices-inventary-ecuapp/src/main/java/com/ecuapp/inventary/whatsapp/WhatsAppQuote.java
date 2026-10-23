package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="whatsapp_quote",uniqueConstraints={
 @UniqueConstraint(name="uk_whatsapp_quote_number",columnNames="quote_number"),
 @UniqueConstraint(name="uk_whatsapp_quote_idempotency",columnNames="idempotency_key")})
public class WhatsAppQuote {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="quote_number",nullable=false,length=48) private String quoteNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cart_id",nullable=false) private WhatsAppCart cart;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private QuoteStatus status=QuoteStatus.ACTIVE;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal tax;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @OneToMany(mappedBy="quote",cascade=CascadeType.ALL,orphanRemoval=true) private List<WhatsAppQuoteItem> items=new ArrayList<>();
 protected WhatsAppQuote(){}
 public WhatsAppQuote(String number,WhatsAppCart cart,Warehouse warehouse,String currency,BigDecimal subtotal,BigDecimal tax,BigDecimal total,String key,String hash,Instant expiresAt){
  this.quoteNumber=number;this.cart=cart;this.warehouse=warehouse;this.status=QuoteStatus.ACTIVE;this.currency=currency;this.subtotal=subtotal;this.tax=tax;this.total=total;this.idempotencyKey=key;this.requestHash=hash;this.expiresAt=expiresAt;
 }
 @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
 public void addItem(WhatsAppQuoteItem item){items.add(item);item.setQuote(this);}
 public void markOrdered(){status=QuoteStatus.ORDERED;}
 public void expire(){if(status==QuoteStatus.ACTIVE)status=QuoteStatus.EXPIRED;}
 public UUID getId(){return id;} public String getQuoteNumber(){return quoteNumber;} public WhatsAppCart getCart(){return cart;} public Warehouse getWarehouse(){return warehouse;}
 public QuoteStatus getStatus(){return status;} public String getCurrency(){return currency;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getTax(){return tax;} public BigDecimal getTotal(){return total;}
 public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;} public Instant getExpiresAt(){return expiresAt;} public Instant getCreatedAt(){return createdAt;} public List<WhatsAppQuoteItem> getItems(){return items;}
}

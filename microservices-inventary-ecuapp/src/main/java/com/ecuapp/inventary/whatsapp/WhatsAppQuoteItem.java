package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="whatsapp_quote_item",uniqueConstraints=@UniqueConstraint(name="uk_whatsapp_quote_item_product",columnNames={"quote_id","product_id"}))
public class WhatsAppQuoteItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="quote_id",nullable=false) private WhatsAppQuote quote;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="sku_snapshot",nullable=false,length=64) private String skuSnapshot;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_price",nullable=false,precision=14,scale=2) private BigDecimal unitPrice;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected WhatsAppQuoteItem(){}
 public WhatsAppQuoteItem(Product product,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){this.product=product;this.skuSnapshot=product.getSku();this.productNameSnapshot=product.getName();this.quantity=quantity;this.unitPrice=unitPrice;this.lineTotal=lineTotal;}
 void setQuote(WhatsAppQuote quote){this.quote=quote;}
 public UUID getId(){return id;} public WhatsAppQuote getQuote(){return quote;} public Product getProduct(){return product;} public String getSkuSnapshot(){return skuSnapshot;} public String getProductNameSnapshot(){return productNameSnapshot;}
 public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}

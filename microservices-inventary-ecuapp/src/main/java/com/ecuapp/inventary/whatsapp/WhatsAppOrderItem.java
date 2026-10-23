package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="whatsapp_order_item",uniqueConstraints=@UniqueConstraint(name="uk_whatsapp_order_item_product",columnNames={"order_id","product_id"}))
public class WhatsAppOrderItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private WhatsAppOrder order;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="sku_snapshot",nullable=false,length=64) private String skuSnapshot;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_price",nullable=false,precision=14,scale=2) private BigDecimal unitPrice;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected WhatsAppOrderItem(){}
 public WhatsAppOrderItem(WhatsAppQuoteItem quoteItem){this.product=quoteItem.getProduct();this.skuSnapshot=quoteItem.getSkuSnapshot();this.productNameSnapshot=quoteItem.getProductNameSnapshot();this.quantity=quoteItem.getQuantity();this.unitPrice=quoteItem.getUnitPrice();this.lineTotal=quoteItem.getLineTotal();}
 void setOrder(WhatsAppOrder order){this.order=order;}
 public UUID getId(){return id;} public WhatsAppOrder getOrder(){return order;} public Product getProduct(){return product;}
 public String getSkuSnapshot(){return skuSnapshot;} public String getProductNameSnapshot(){return productNameSnapshot;}
 public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}

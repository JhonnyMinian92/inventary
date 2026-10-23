package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="sale_item")
public class SaleItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sale_id",nullable=false) private Sale sale;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="sku_snapshot",nullable=false,length=64) private String skuSnapshot;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_price",nullable=false,precision=14,scale=2) private BigDecimal unitPrice;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected SaleItem(){}
 public SaleItem(Product product,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){
  this.product=product;this.skuSnapshot=product.getSku();this.productNameSnapshot=product.getName();this.quantity=quantity;this.unitPrice=unitPrice;this.lineTotal=lineTotal;
 }
 void setSale(Sale sale){this.sale=sale;}
 public UUID getId(){return id;} public Product getProduct(){return product;} public String getSkuSnapshot(){return skuSnapshot;}
 public String getProductNameSnapshot(){return productNameSnapshot;} public BigDecimal getQuantity(){return quantity;}
 public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}

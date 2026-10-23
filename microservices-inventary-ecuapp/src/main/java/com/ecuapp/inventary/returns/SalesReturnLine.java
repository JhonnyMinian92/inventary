package com.ecuapp.inventary.returns;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.sales.SaleItem;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="sales_return_line",uniqueConstraints=@UniqueConstraint(name="uk_sales_return_source_item",columnNames={"sales_return_id","source_sale_item_id"}))
public class SalesReturnLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sales_return_id",nullable=false) private SalesReturn salesReturn;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_sale_item_id",nullable=false) private SaleItem sourceSaleItem;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private ReturnDisposition disposition;
 @Column(name="unit_price",nullable=false,precision=14,scale=2) private BigDecimal unitPrice;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected SalesReturnLine(){}
 public SalesReturnLine(SaleItem sourceSaleItem,BigDecimal quantity,ReturnDisposition disposition){
  this.sourceSaleItem=sourceSaleItem;this.product=sourceSaleItem.getProduct();this.quantity=quantity;this.disposition=disposition;
  this.unitPrice=sourceSaleItem.getUnitPrice();this.lineTotal=unitPrice.multiply(quantity).setScale(2,java.math.RoundingMode.HALF_UP);
 }
 void setSalesReturn(SalesReturn salesReturn){this.salesReturn=salesReturn;}
 public UUID getId(){return id;} public SalesReturn getSalesReturn(){return salesReturn;} public SaleItem getSourceSaleItem(){return sourceSaleItem;}
 public Product getProduct(){return product;} public BigDecimal getQuantity(){return quantity;} public ReturnDisposition getDisposition(){return disposition;}
 public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
}

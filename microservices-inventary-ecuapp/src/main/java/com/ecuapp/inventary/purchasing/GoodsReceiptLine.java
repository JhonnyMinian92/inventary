package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="goods_receipt_line",uniqueConstraints=@UniqueConstraint(name="uk_goods_receipt_po_line",columnNames={"receipt_id","purchase_order_line_id"}))
public class GoodsReceiptLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="receipt_id",nullable=false) private GoodsReceipt receipt;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_order_line_id",nullable=false) private PurchaseOrderLine purchaseOrderLine;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=2) private BigDecimal unitCost;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected GoodsReceiptLine(){}
 public GoodsReceiptLine(PurchaseOrderLine line,BigDecimal quantity){
  this.purchaseOrderLine=line;this.product=line.getProduct();this.quantity=quantity;this.unitCost=line.getUnitCost();this.lineTotal=unitCost.multiply(quantity).setScale(2,java.math.RoundingMode.HALF_UP);
 }
 void setReceipt(GoodsReceipt receipt){this.receipt=receipt;}
 public UUID getId(){return id;} public PurchaseOrderLine getPurchaseOrderLine(){return purchaseOrderLine;} public Product getProduct(){return product;}
 public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getLineTotal(){return lineTotal;}
}

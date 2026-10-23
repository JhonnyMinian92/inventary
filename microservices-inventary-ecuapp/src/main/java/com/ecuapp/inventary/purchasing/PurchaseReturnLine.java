package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="purchase_return_line",uniqueConstraints=@UniqueConstraint(name="uk_purchase_return_receipt_line",columnNames={"purchase_return_id","source_goods_receipt_line_id"}))
public class PurchaseReturnLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_return_id",nullable=false) private PurchaseReturn purchaseReturn;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_goods_receipt_line_id",nullable=false) private GoodsReceiptLine sourceReceiptLine;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="unit_cost",nullable=false,precision=14,scale=2) private BigDecimal unitCost;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected PurchaseReturnLine(){}
 public PurchaseReturnLine(GoodsReceiptLine source,BigDecimal quantity){this.sourceReceiptLine=source;this.product=source.getProduct();this.quantity=quantity;this.unitCost=source.getUnitCost();this.lineTotal=unitCost.multiply(quantity).setScale(2,java.math.RoundingMode.HALF_UP);}
 void setPurchaseReturn(PurchaseReturn p){this.purchaseReturn=p;}
 public UUID getId(){return id;} public GoodsReceiptLine getSourceReceiptLine(){return sourceReceiptLine;} public Product getProduct(){return product;}
 public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getLineTotal(){return lineTotal;}
}

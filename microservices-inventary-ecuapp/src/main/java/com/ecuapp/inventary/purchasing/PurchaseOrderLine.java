package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="purchase_order_line")
public class PurchaseOrderLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="purchase_order_id",nullable=false) private PurchaseOrder purchaseOrder;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="sku_snapshot",nullable=false,length=64) private String skuSnapshot;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(name="ordered_quantity",nullable=false,precision=14,scale=3) private BigDecimal orderedQuantity;
 @Column(name="received_quantity",nullable=false,precision=14,scale=3) private BigDecimal receivedQuantity=BigDecimal.ZERO.setScale(3);
 @Column(name="unit_cost",nullable=false,precision=14,scale=2) private BigDecimal unitCost;
 @Column(name="line_total",nullable=false,precision=14,scale=2) private BigDecimal lineTotal;
 protected PurchaseOrderLine(){}
 public PurchaseOrderLine(Product product,BigDecimal quantity,BigDecimal unitCost,BigDecimal lineTotal){
  this.product=product;this.skuSnapshot=product.getSku();this.productNameSnapshot=product.getName();this.orderedQuantity=quantity;this.receivedQuantity=BigDecimal.ZERO.setScale(3);this.unitCost=unitCost;this.lineTotal=lineTotal;
 }
 void setPurchaseOrder(PurchaseOrder order){this.purchaseOrder=order;}
 public void receive(BigDecimal quantity){this.receivedQuantity=this.receivedQuantity.add(quantity).setScale(3);}
 public BigDecimal remaining(){return this.orderedQuantity.subtract(this.receivedQuantity).setScale(3);}
 public UUID getId(){return id;} public PurchaseOrder getPurchaseOrder(){return purchaseOrder;} public Product getProduct(){return product;}
 public String getSkuSnapshot(){return skuSnapshot;} public String getProductNameSnapshot(){return productNameSnapshot;}
 public BigDecimal getOrderedQuantity(){return orderedQuantity;} public BigDecimal getReceivedQuantity(){return receivedQuantity;}
 public BigDecimal getUnitCost(){return unitCost;} public BigDecimal getLineTotal(){return lineTotal;}
}

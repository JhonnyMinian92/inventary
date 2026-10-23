package com.ecuapp.inventary.transfers;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="stock_transfer_line",uniqueConstraints=@UniqueConstraint(name="uk_stock_transfer_line_product",columnNames={"transfer_id","product_id"}))
public class StockTransferLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="transfer_id",nullable=false) private StockTransfer transfer;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="sku_snapshot",nullable=false,length=64) private String skuSnapshot;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(name="requested_quantity",nullable=false,precision=14,scale=3) private BigDecimal requestedQuantity;
 @Column(name="received_quantity",nullable=false,precision=14,scale=3) private BigDecimal receivedQuantity=BigDecimal.ZERO.setScale(3);
 @Column(name="unit_cost",nullable=false,precision=14,scale=2) private BigDecimal unitCost;
 protected StockTransferLine(){}
 public StockTransferLine(Product product,BigDecimal quantity,BigDecimal unitCost){
  this.product=product;this.skuSnapshot=product.getSku();this.productNameSnapshot=product.getName();this.requestedQuantity=quantity;
  this.receivedQuantity=BigDecimal.ZERO.setScale(3);this.unitCost=unitCost;
 }
 void setTransfer(StockTransfer transfer){this.transfer=transfer;}
 public void receive(){this.receivedQuantity=this.requestedQuantity;}
 public UUID getId(){return id;} public StockTransfer getTransfer(){return transfer;} public Product getProduct(){return product;}
 public String getSkuSnapshot(){return skuSnapshot;} public String getProductNameSnapshot(){return productNameSnapshot;}
 public BigDecimal getRequestedQuantity(){return requestedQuantity;} public BigDecimal getReceivedQuantity(){return receivedQuantity;}
 public BigDecimal getUnitCost(){return unitCost;}
}

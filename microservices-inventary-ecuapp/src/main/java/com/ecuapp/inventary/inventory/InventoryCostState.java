package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Running quantity and value for one product in one stock location; the average cost is value / quantity. */
@Entity
@Table(name="inventory_cost_state",uniqueConstraints=@UniqueConstraint(name="uk_inventory_cost_state_product_location",columnNames={"product_id","location_id"}))
public class InventoryCostState {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="location_id",nullable=false) private WarehouseLocation location;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantity;
 @Column(name="total_value",nullable=false,precision=18,scale=4) private BigDecimal totalValue;
 @Column(nullable=false,length=3) private String currency;
 @Version @Column(nullable=false) private long version;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected InventoryCostState(){}
 @PrePersist @PreUpdate void touch(){updatedAt=Instant.now();}
 void add(BigDecimal qty,BigDecimal value){quantity=quantity.add(qty).setScale(3);totalValue=totalValue.add(value).setScale(4,RoundingMode.HALF_UP);}
 void remove(BigDecimal qty,BigDecimal value){
  quantity=quantity.subtract(qty).max(BigDecimal.ZERO).setScale(3);
  totalValue=quantity.signum()==0?BigDecimal.ZERO.setScale(4):totalValue.subtract(value).max(BigDecimal.ZERO).setScale(4,RoundingMode.HALF_UP);
 }
 void replaceValue(BigDecimal value){totalValue=value.max(BigDecimal.ZERO).setScale(4,RoundingMode.HALF_UP);}
 /** Weighted average unit cost, or null when the location holds no valued stock. */
 public BigDecimal averageCost(){return quantity.signum()==0?null:totalValue.divide(quantity,4,RoundingMode.HALF_UP);}
 public UUID getId(){return id;} public Product getProduct(){return product;} public WarehouseLocation getLocation(){return location;}
 public BigDecimal getQuantity(){return quantity;} public BigDecimal getTotalValue(){return totalValue;} public String getCurrency(){return currency;}
 public long getVersion(){return version;} public Instant getUpdatedAt(){return updatedAt;}
}

package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="reorder_rule",uniqueConstraints=@UniqueConstraint(name="uk_reorder_rule_product_warehouse",columnNames={"product_id","warehouse_id"}))
public class ReorderRule {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @Column(name="minimum_quantity",nullable=false,precision=14,scale=3) private BigDecimal minimumQuantity;
 @Column(name="target_quantity",nullable=false,precision=14,scale=3) private BigDecimal targetQuantity;
 @Column(name="lead_time_days",nullable=false) private int leadTimeDays;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="preferred_supplier_id") private BusinessPartner preferredSupplier;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected ReorderRule(){}
 public ReorderRule(Product product,Warehouse warehouse,BigDecimal minimum,BigDecimal target,int leadTimeDays,BusinessPartner supplier){this.product=product;this.warehouse=warehouse;this.minimumQuantity=minimum;this.targetQuantity=target;this.leadTimeDays=leadTimeDays;this.preferredSupplier=supplier;this.active=true;}
 @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void update(BigDecimal minimum,BigDecimal target,int leadTimeDays,BusinessPartner supplier){this.minimumQuantity=minimum;this.targetQuantity=target;this.leadTimeDays=leadTimeDays;this.preferredSupplier=supplier;}
 public void deactivate(){active=false;}
 public UUID getId(){return id;} public Product getProduct(){return product;} public Warehouse getWarehouse(){return warehouse;}
 public BigDecimal getMinimumQuantity(){return minimumQuantity;} public BigDecimal getTargetQuantity(){return targetQuantity;} public int getLeadTimeDays(){return leadTimeDays;}
 public BusinessPartner getPreferredSupplier(){return preferredSupplier;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}

package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="product", uniqueConstraints=@UniqueConstraint(name="uk_product_sku",columnNames="sku"))
public class Product {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=64) private String sku;
 @Column(nullable=false,length=180) private String name;
 @Column(length=2000) private String description;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id",nullable=false,foreignKey=@ForeignKey(name="fk_product_category")) private Category category;
 @Column(name="cost_price",nullable=false,precision=14,scale=2) private BigDecimal costPrice;
 @Column(name="sale_price",nullable=false,precision=14,scale=2) private BigDecimal salePrice;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="image_url",length=2048) private String imageUrl;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Version @Column(nullable=false) private long version;
 protected Product(){}
 public Product(String sku,String name,String description,Category category,BigDecimal costPrice,BigDecimal salePrice,String currency,String imageUrl){
  this.sku=sku;this.name=name;this.description=description;this.category=category;this.costPrice=costPrice;this.salePrice=salePrice;this.currency=currency;this.imageUrl=imageUrl;this.active=true;
 }
 @PrePersist void onCreate(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public void update(String name,String description,Category category,BigDecimal costPrice,BigDecimal salePrice,String currency,String imageUrl){
  this.name=name;this.description=description;this.category=category;this.costPrice=costPrice;this.salePrice=salePrice;this.currency=currency;this.imageUrl=imageUrl;
 }
 public void deactivate(){active=false;}
 public UUID getId(){return id;} public String getSku(){return sku;} public String getName(){return name;}
 public String getDescription(){return description;} public Category getCategory(){return category;}
 public BigDecimal getCostPrice(){return costPrice;} public BigDecimal getSalePrice(){return salePrice;}
 public String getCurrency(){return currency;} public String getImageUrl(){return imageUrl;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}

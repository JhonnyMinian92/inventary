package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sellable presentation of a product (size, color...). Stock and valuation are kept at product level;
 * a variant contributes its own SKU, barcode, option values and optional price.
 */
@Entity
@Table(name="product_variant",uniqueConstraints={
 @UniqueConstraint(name="uk_product_variant_sku",columnNames="sku"),
 @UniqueConstraint(name="uk_product_variant_barcode",columnNames="barcode")})
public class ProductVariant {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(nullable=false,length=64) private String sku;
 @Column(length=128) private String barcode;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="option_values",nullable=false,columnDefinition="jsonb") private Map<String,Object> optionValues;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="unit_of_measure_id",nullable=false) private UnitOfMeasure unitOfMeasure;
 @Column(name="sale_price",precision=14,scale=2) private BigDecimal salePrice;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected ProductVariant(){}
 public ProductVariant(Product product,String sku,String barcode,Map<String,Object> optionValues,UnitOfMeasure unit,BigDecimal salePrice){
  this.product=product;this.sku=sku;this.barcode=barcode;this.optionValues=Map.copyOf(optionValues);this.unitOfMeasure=unit;this.salePrice=salePrice;this.active=true;
 }
 @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
 public void update(String barcode,Map<String,Object> optionValues,BigDecimal salePrice){this.barcode=barcode;this.optionValues=Map.copyOf(optionValues);this.salePrice=salePrice;}
 public void deactivate(){active=false;}
 public UUID getId(){return id;} public Product getProduct(){return product;} public String getSku(){return sku;} public String getBarcode(){return barcode;} public Map<String,Object> getOptionValues(){return optionValues;} public UnitOfMeasure getUnitOfMeasure(){return unitOfMeasure;} public BigDecimal getSalePrice(){return salePrice;} public boolean isActive(){return active;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}

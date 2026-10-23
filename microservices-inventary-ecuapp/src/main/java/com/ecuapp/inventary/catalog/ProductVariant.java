package com.ecuapp.inventary.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
 @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}
 @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public Product getProduct(){return product;} public String getSku(){return sku;} public String getBarcode(){return barcode;} public Map<String,Object> getOptionValues(){return optionValues;} public UnitOfMeasure getUnitOfMeasure(){return unitOfMeasure;} public BigDecimal getSalePrice(){return salePrice;} public boolean isActive(){return active;}
}

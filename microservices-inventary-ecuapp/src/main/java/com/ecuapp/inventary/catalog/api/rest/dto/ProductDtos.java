package com.ecuapp.inventary.catalog.api.rest.dto;

import com.ecuapp.inventary.catalog.*;

import com.ecuapp.inventary.catalog.application.ProductResult;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductDtos {
 private ProductDtos(){}
 /**
  * sku, prices, marginPercent, kind, unitOfMeasureId and attributes are optional; on update a null value keeps the current one.
  * When sku is omitted on creation the backend generates a unique random code; it is immutable afterwards.
  * Prices are normally derived: received purchases set the cost and the sale price is cost plus marginPercent (default 25).
  */
 public record WriteRequest(@Size(max=64) @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$",message="El SKU solo admite letras, números, punto, guion y guion bajo.") String sku,
  @NotBlank @Size(max=180) String name,
  @Size(max=2000) String description,@NotNull UUID categoryId,
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal costPrice,
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal salePrice,
  @Pattern(regexp="[A-Z]{3}") String currency,
  @Size(max=2048) @Pattern(regexp="https://[^\\s]+",message="imageUrl debe ser una URL HTTPS") String imageUrl,
  ProductKind kind,UUID unitOfMeasureId,@Size(max=50) Map<String,Object> attributes,
  @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer=4,fraction=2) BigDecimal marginPercent,
  ServiceBilling serviceBilling,BillingFrequency billingFrequency,Integer termMonths){
  public WriteRequest(String sku,String name,String description,UUID categoryId,BigDecimal costPrice,BigDecimal salePrice,String currency,String imageUrl){
   this(sku,name,description,categoryId,costPrice,salePrice,currency,imageUrl,null,null,null,null,null,null,null);
  }
 }
 /** costPrice is only populated for callers allowed to see costs (catalog:manage or reports:financial). */
 public record View(UUID id,String sku,String name,String description,UUID categoryId,String categoryName,
  BigDecimal costPrice,BigDecimal salePrice,BigDecimal marginPercent,String currency,String imageUrl,ProductKind kind,UUID unitOfMeasureId,
  Map<String,Object> attributes,int attributeSchemaVersion,ServiceBilling serviceBilling,BillingFrequency billingFrequency,Integer termMonths,
  boolean active,Instant createdAt,Instant updatedAt){
  public static View from(ProductResult p,boolean includeCost){return new View(p.id(),p.sku(),p.name(),p.description(),p.categoryId(),p.categoryName(),
   includeCost?p.costPrice():null,p.salePrice(),includeCost?p.marginPercent():null,p.currency(),p.imageUrl(),p.kind(),p.unitOfMeasureId(),p.attributes(),p.attributeSchemaVersion(),p.serviceBilling(),p.billingFrequency(),p.termMonths(),p.active(),p.createdAt(),p.updatedAt());}
 }
 public record PublicView(UUID id,String sku,String name,String description,String categoryName,BigDecimal price,String currency,String imageUrl,boolean available){
  public static PublicView from(Product p,boolean available){return new PublicView(p.getId(),p.getSku(),p.getName(),p.getDescription(),p.getCategory().getName(),p.getSalePrice(),p.getCurrency(),p.getImageUrl(),available);}
 }
 public record PublicVariantView(String sku,String barcode,Map<String,Object> optionValues,BigDecimal price){}
 public record PublicImageView(String url,String altText,boolean primary,int sortOrder){}
 public record PublicDetailView(UUID id,String sku,String name,String description,UUID categoryId,String categoryName,BigDecimal price,String currency,
  String imageUrl,boolean available,Map<String,Object> attributes,List<PublicVariantView> variants,List<PublicImageView> images){}
 public record PublicCategoryView(UUID id,String name,String slug,String description){
  public static PublicCategoryView from(Category c){return new PublicCategoryView(c.getId(),c.getName(),c.getSlug(),c.getDescription());}
 }
}

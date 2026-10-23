package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.application.ProductResult;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProductDtos {
 private ProductDtos(){}
 public record WriteRequest(@NotBlank @Size(max=64) String sku,@NotBlank @Size(max=180) String name,
  @Size(max=2000) String description,@NotNull UUID categoryId,
  @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal costPrice,
  @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal salePrice,
  @Pattern(regexp="[A-Z]{3}") String currency,
  @Size(max=2048) @Pattern(regexp="https://.*",message="imageUrl debe ser una URL HTTPS") String imageUrl){}
 public record View(UUID id,String sku,String name,String description,UUID categoryId,String categoryName,
  BigDecimal salePrice,String currency,String imageUrl,boolean active,Instant createdAt,Instant updatedAt){
  static View from(Product p){return new View(p.getId(),p.getSku(),p.getName(),p.getDescription(),p.getCategory().getId(),p.getCategory().getName(),p.getSalePrice(),p.getCurrency(),p.getImageUrl(),p.isActive(),p.getCreatedAt(),p.getUpdatedAt());}
  static View from(ProductResult p){return new View(p.id(),p.sku(),p.name(),p.description(),p.categoryId(),p.categoryName(),p.salePrice(),p.currency(),p.imageUrl(),p.active(),p.createdAt(),p.updatedAt());}
 }
 public record PublicView(UUID id,String sku,String name,String description,String categoryName,BigDecimal price,String currency,String imageUrl,boolean available){
  static PublicView from(Product p,boolean available){return new PublicView(p.getId(),p.getSku(),p.getName(),p.getDescription(),p.getCategory().getName(),p.getSalePrice(),p.getCurrency(),p.getImageUrl(),available);}
 }
}

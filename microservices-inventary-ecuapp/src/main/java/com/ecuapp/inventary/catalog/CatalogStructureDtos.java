package com.ecuapp.inventary.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogStructureDtos {
 private CatalogStructureDtos(){}
 public record UnitCreateRequest(@NotBlank @Pattern(regexp="^[A-Za-z][A-Za-z0-9_]{0,23}$",message="Código de unidad inválido") String code,
  @NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="^[A-Za-z][A-Za-z_]{1,31}$",message="Dimensión inválida") String dimension,
  @NotNull @Min(0) @Max(3) Integer decimalScale){}
 public record UnitUpdateRequest(@NotBlank @Size(max=100) String name,@NotNull @Min(0) @Max(3) Integer decimalScale,@NotNull Boolean active){}
 public record UnitView(UUID id,String code,String name,String dimension,int decimalScale,boolean active){
  static UnitView from(UnitOfMeasure u){return new UnitView(u.getId(),u.getCode(),u.getName(),u.getDimension(),u.getDecimalScale(),u.isActive());}
 }
 public record ConversionRequest(@NotNull UUID fromUnitId,@NotNull UUID toUnitId,
  @NotNull @DecimalMin(value="0.000000001",inclusive=true) @Digits(integer=11,fraction=9) BigDecimal factor){}
 public record ConversionView(UUID id,UUID fromUnitId,String fromUnitCode,UUID toUnitId,String toUnitCode,BigDecimal factor){
  static ConversionView from(UnitConversion c){return new ConversionView(c.getId(),c.getFromUnit().getId(),c.getFromUnit().getCode(),c.getToUnit().getId(),c.getToUnit().getCode(),c.getFactor());}
 }
 public record AttributeDefinitionRequest(@NotBlank @Size(max=80) String key,@NotBlank @Size(max=120) String displayName,
  @NotNull CategoryAttributeDefinition.ValueType valueType,boolean required,Map<String,Object> constraints,@Size(max=200) List<Object> options){}
 public record AttributeSchemaRequest(@NotNull @Size(max=50) List<@Valid AttributeDefinitionRequest> definitions){}
 public record AttributeDefinitionView(UUID id,String key,String displayName,CategoryAttributeDefinition.ValueType valueType,boolean required,
  Map<String,Object> constraints,List<Object> options){
  static AttributeDefinitionView from(CategoryAttributeDefinition d){return new AttributeDefinitionView(d.getId(),d.getAttributeKey(),d.getDisplayName(),d.getValueType(),d.isRequired(),d.getConstraints(),d.getOptions());}
 }
 public record AttributeSchemaView(UUID categoryId,int schemaVersion,List<AttributeDefinitionView> definitions){}
 public record VariantWriteRequest(@NotBlank @Size(max=64) String sku,@Size(max=128) @Pattern(regexp="^[A-Za-z0-9-]{4,128}$",message="Código de barras inválido") String barcode,
  @NotNull @Size(min=1,max=20) Map<String,Object> optionValues,UUID unitOfMeasureId,
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal salePrice){}
 public record VariantUpdateRequest(@Size(max=128) @Pattern(regexp="^[A-Za-z0-9-]{4,128}$",message="Código de barras inválido") String barcode,
  @NotNull @Size(min=1,max=20) Map<String,Object> optionValues,@DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal salePrice){}
 public record VariantView(UUID id,UUID productId,String sku,String barcode,Map<String,Object> optionValues,UUID unitOfMeasureId,String unitOfMeasureCode,
  BigDecimal salePrice,BigDecimal effectivePrice,boolean active,Instant createdAt,Instant updatedAt){
  static VariantView from(ProductVariant v){return new VariantView(v.getId(),v.getProduct().getId(),v.getSku(),v.getBarcode(),v.getOptionValues(),v.getUnitOfMeasure().getId(),
   v.getUnitOfMeasure().getCode(),v.getSalePrice(),v.getSalePrice()==null?v.getProduct().getSalePrice():v.getSalePrice(),v.isActive(),v.getCreatedAt(),v.getUpdatedAt());}
 }
}

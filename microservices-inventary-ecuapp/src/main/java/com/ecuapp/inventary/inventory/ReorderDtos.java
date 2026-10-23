package com.ecuapp.inventary.inventory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class ReorderDtos {
 private ReorderDtos(){}
 public record WriteRequest(@NotNull UUID productId,@NotNull UUID warehouseId,
  @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) BigDecimal minimumQuantity,
  @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) BigDecimal targetQuantity,
  @Min(0) @Max(3650) int leadTimeDays,UUID preferredSupplierId){}
 public record View(UUID id,UUID productId,String sku,String productName,UUID warehouseId,String warehouseCode,
  BigDecimal minimumQuantity,BigDecimal targetQuantity,int leadTimeDays,UUID preferredSupplierId,String preferredSupplierName,
  boolean active,Instant createdAt,Instant updatedAt){
  static View from(ReorderRule r){return new View(r.getId(),r.getProduct().getId(),r.getProduct().getSku(),r.getProduct().getName(),
   r.getWarehouse().getId(),r.getWarehouse().getCode(),r.getMinimumQuantity(),r.getTargetQuantity(),r.getLeadTimeDays(),
   r.getPreferredSupplier()==null?null:r.getPreferredSupplier().getId(),r.getPreferredSupplier()==null?null:r.getPreferredSupplier().getName(),
   r.isActive(),r.getCreatedAt(),r.getUpdatedAt());}
 }
 public record Suggestion(UUID productId,String sku,String productName,UUID warehouseId,String warehouseCode,BigDecimal available,
  BigDecimal minimumQuantity,BigDecimal targetQuantity,BigDecimal suggestedQuantity,int leadTimeDays,UUID preferredSupplierId,String preferredSupplierName){}
}

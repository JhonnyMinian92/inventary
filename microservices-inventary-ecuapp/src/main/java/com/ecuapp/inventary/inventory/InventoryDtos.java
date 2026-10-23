package com.ecuapp.inventary.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {
 private InventoryDtos(){}
 public enum Direction { INCREASE, DECREASE }
 public record AdjustmentRequest(@NotNull UUID productId,@NotNull UUID warehouseId,@NotNull Direction direction,
   @NotNull @DecimalMin(value="0.000",inclusive=false) @Digits(integer=11,fraction=3) BigDecimal quantity,
   @NotBlank @Size(max=1000) String reason){}
 public record BalanceView(UUID id,UUID productId,String sku,String productName,UUID warehouseId,String warehouseCode,
   BigDecimal onHand,BigDecimal reserved,BigDecimal available,long version){
  static BalanceView from(StockBalance b){return new BalanceView(b.getId(),b.getProduct().getId(),b.getProduct().getSku(),b.getProduct().getName(),b.getWarehouse().getId(),b.getWarehouse().getCode(),b.getOnHand(),b.getReserved(),b.available(),b.getVersion());}
 }
 public record MovementView(UUID id,UUID productId,String sku,UUID warehouseId,String warehouseCode,MovementType type,
   BigDecimal quantity,BigDecimal unitCost,String referenceType,UUID referenceId,String notes,String createdBy,Instant occurredAt){
  static MovementView from(StockMovement m){return new MovementView(m.getId(),m.getProduct().getId(),m.getProduct().getSku(),m.getWarehouse().getId(),m.getWarehouse().getCode(),m.getMovementType(),m.getQuantity(),m.getUnitCost(),m.getReferenceType(),m.getReferenceId(),m.getNotes(),m.getCreatedBy(),m.getOccurredAt());}
 }
}

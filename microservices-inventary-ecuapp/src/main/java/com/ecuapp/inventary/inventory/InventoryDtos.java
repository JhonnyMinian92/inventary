package com.ecuapp.inventary.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
 private InventoryDtos(){}
 public enum Direction { INCREASE, DECREASE }
 /** unitCost is optional and only used by INCREASE; by default the current valued cost is applied. */
 public record AdjustmentRequest(@NotNull UUID productId,@NotNull UUID warehouseId,@NotNull Direction direction,
   @NotNull @DecimalMin(value="0.000",inclusive=false) @Digits(integer=11,fraction=3) BigDecimal quantity,
   @NotBlank @Size(max=1000) String reason,
   @DecimalMin("0.0000") @Digits(integer=10,fraction=4) BigDecimal unitCost){
  public AdjustmentRequest(UUID productId,UUID warehouseId,Direction direction,BigDecimal quantity,String reason){this(productId,warehouseId,direction,quantity,reason,null);}
 }
 public record BalanceView(UUID id,UUID productId,String sku,String productName,UUID warehouseId,String warehouseCode,
   BigDecimal onHand,BigDecimal reserved,BigDecimal available,long version){
  static BalanceView from(StockBalance b){return new BalanceView(b.getId(),b.getProduct().getId(),b.getProduct().getSku(),b.getProduct().getName(),b.getWarehouse().getId(),b.getWarehouse().getCode(),b.getOnHand(),b.getReserved(),b.available(),b.getVersion());}
 }
 public record AvailabilityView(UUID productId,String sku,UUID warehouseId,String warehouseCode,BigDecimal onHand,BigDecimal reserved,
   BigDecimal available,BigDecimal requested,boolean sufficient){}
 public record MovementView(UUID id,UUID productId,String sku,UUID warehouseId,String warehouseCode,MovementType type,
   BigDecimal quantity,BigDecimal unitCost,String referenceType,UUID referenceId,UUID fromLocationId,UUID toLocationId,
   String unitOfMeasureCode,UUID sourceLineId,String correlationId,CostingMethod valuationMethod,String notes,String createdBy,Instant occurredAt){
  static MovementView from(StockMovement m,boolean includeCost){return new MovementView(m.getId(),m.getProduct().getId(),m.getProduct().getSku(),m.getWarehouse().getId(),m.getWarehouse().getCode(),m.getMovementType(),m.getQuantity(),includeCost?m.getUnitCost():null,m.getReferenceType(),m.getReferenceId(),m.getFromLocation()==null?null:m.getFromLocation().getId(),m.getToLocation()==null?null:m.getToLocation().getId(),m.getUnitOfMeasure()==null?null:m.getUnitOfMeasure().getCode(),m.getSourceLineId(),m.getCorrelationId(),includeCost?m.getValuationMethod():null,m.getNotes(),m.getCreatedBy(),m.getOccurredAt());}
 }
 public record ValuationEntryView(UUID id,UUID costLayerId,CostingMethod method,BigDecimal quantity,BigDecimal unitCost,BigDecimal totalCost,String currency){
  static ValuationEntryView from(InventoryValuationEntry e){return new ValuationEntryView(e.getId(),e.getCostLayer()==null?null:e.getCostLayer().getId(),e.getMethod(),e.getQuantity(),e.getUnitCost(),e.getTotalCost(),e.getCurrency());}
 }
 public record MovementDetailView(MovementView movement,BigDecimal valuedTotalCost,List<ValuationEntryView> valuation){}
}

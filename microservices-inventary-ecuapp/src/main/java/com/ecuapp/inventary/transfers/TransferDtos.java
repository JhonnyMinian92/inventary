package com.ecuapp.inventary.transfers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransferDtos {
 private TransferDtos(){}
 public record LineRequest(@NotNull UUID productId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record CreateRequest(@NotNull UUID sourceWarehouseId,@NotNull UUID destinationWarehouseId,@NotEmpty @Size(max=100) List<@Valid LineRequest> items){}
 public record LineView(UUID id,UUID productId,String sku,String productName,BigDecimal quantity,BigDecimal receivedQuantity,BigDecimal unitCost){}
 public record View(UUID id,String transferNumber,TransferStatus status,UUID sourceWarehouseId,String sourceWarehouseCode,
  UUID destinationWarehouseId,String destinationWarehouseCode,String createdBy,Instant createdAt,Instant dispatchedAt,Instant completedAt,List<LineView> items){
  static View from(StockTransfer t){return new View(t.getId(),t.getTransferNumber(),t.getStatus(),t.getSourceWarehouse().getId(),t.getSourceWarehouse().getCode(),
   t.getDestinationWarehouse().getId(),t.getDestinationWarehouse().getCode(),t.getCreatedBy(),t.getCreatedAt(),t.getDispatchedAt(),t.getCompletedAt(),
   t.getLines().stream().map(l->new LineView(l.getId(),l.getProduct().getId(),l.getSkuSnapshot(),l.getProductNameSnapshot(),l.getRequestedQuantity(),l.getReceivedQuantity(),l.getUnitCost())).toList());}
 }
}

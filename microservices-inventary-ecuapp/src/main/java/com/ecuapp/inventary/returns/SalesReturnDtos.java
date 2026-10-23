package com.ecuapp.inventary.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SalesReturnDtos {
 private SalesReturnDtos(){}
 public record LineRequest(@NotNull UUID saleItemId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity,@NotNull ReturnDisposition disposition){}
 public record CreateRequest(@NotNull UUID sourceSaleId,@NotBlank @Size(max=1000) String reason,@NotEmpty @Size(max=100) List<@Valid LineRequest> items){}
 public record LineView(UUID id,UUID saleItemId,UUID productId,String sku,String productName,BigDecimal quantity,ReturnDisposition disposition,BigDecimal unitPrice,BigDecimal lineTotal){}
 public record View(UUID id,String returnNumber,ReturnStatus status,UUID sourceSaleId,String saleNumber,UUID warehouseId,String warehouseCode,
  String reason,String createdBy,Instant createdAt,BigDecimal total,List<LineView> items){
  static View from(SalesReturn r){BigDecimal total=r.getLines().stream().map(SalesReturnLine::getLineTotal).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
   return new View(r.getId(),r.getReturnNumber(),r.getStatus(),r.getSourceSale().getId(),r.getSourceSale().getSaleNumber(),r.getWarehouse().getId(),r.getWarehouse().getCode(),r.getReason(),r.getCreatedBy(),r.getCreatedAt(),total,
    r.getLines().stream().map(l->new LineView(l.getId(),l.getSourceSaleItem().getId(),l.getProduct().getId(),l.getProduct().getSku(),l.getProduct().getName(),l.getQuantity(),l.getDisposition(),l.getUnitPrice(),l.getLineTotal())).toList());}
 }
}

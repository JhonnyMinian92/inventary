package com.ecuapp.inventary.purchasing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class PurchaseReturnDtos {
 private PurchaseReturnDtos(){}
 public record LineRequest(@NotNull UUID receiptLineId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record CreateRequest(@NotNull UUID goodsReceiptId,@NotBlank @Size(max=1000) String reason,@NotEmpty @Size(max=100) List<@Valid LineRequest> items){}
 public record LineView(UUID id,UUID receiptLineId,UUID productId,String sku,BigDecimal quantity,BigDecimal unitCost,BigDecimal total){}
 public record View(UUID id,String returnNumber,String status,UUID purchaseOrderId,String purchaseOrderNumber,UUID receiptId,String receiptNumber,
  UUID warehouseId,String warehouseCode,String reason,String createdBy,Instant createdAt,BigDecimal total,List<LineView> items){
  static View from(PurchaseReturn r){BigDecimal total=r.getLines().stream().map(PurchaseReturnLine::getLineTotal).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
   return new View(r.getId(),r.getReturnNumber(),r.getStatus(),r.getPurchaseOrder().getId(),r.getPurchaseOrder().getOrderNumber(),r.getGoodsReceipt().getId(),r.getGoodsReceipt().getReceiptNumber(),r.getWarehouse().getId(),r.getWarehouse().getCode(),r.getReason(),r.getCreatedBy(),r.getCreatedAt(),total,
    r.getLines().stream().map(l->new LineView(l.getId(),l.getSourceReceiptLine().getId(),l.getProduct().getId(),l.getProduct().getSku(),l.getQuantity(),l.getUnitCost(),l.getLineTotal())).toList());}
 }
}

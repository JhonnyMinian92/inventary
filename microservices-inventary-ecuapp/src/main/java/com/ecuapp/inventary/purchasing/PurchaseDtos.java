package com.ecuapp.inventary.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PurchaseDtos {
 private PurchaseDtos(){}
 public record OrderLineRequest(@NotNull UUID productId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity,
  @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal unitCost){}
 public record CreateOrderRequest(@NotNull UUID supplierId,@NotNull UUID warehouseId,@Size(max=1000) String notes,
  @NotEmpty @Size(max=100) List<@Valid OrderLineRequest> items){}
 public record ReceiptLineRequest(@NotNull UUID purchaseOrderLineId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record ReceiptRequest(@NotEmpty @Size(max=100) List<@Valid ReceiptLineRequest> items){}
 public record OrderLineView(UUID id,UUID productId,String sku,String productName,BigDecimal orderedQuantity,BigDecimal receivedQuantity,
  BigDecimal remainingQuantity,BigDecimal unitCost,BigDecimal lineTotal){}
 public record OrderView(UUID id,String orderNumber,PurchaseOrderStatus status,UUID supplierId,String supplierName,UUID warehouseId,String warehouseCode,
  String currency,BigDecimal subtotal,BigDecimal total,String notes,String createdBy,Instant orderedAt,String submittedBy,Instant submittedAt,
  String approvedBy,Instant approvedAt,String cancelledBy,Instant cancelledAt,List<OrderLineView> items){
  static OrderView from(PurchaseOrder p){return new OrderView(p.getId(),p.getOrderNumber(),p.getStatus(),p.getSupplier().getId(),p.getSupplier().getName(),p.getWarehouse().getId(),p.getWarehouse().getCode(),p.getCurrency(),p.getSubtotal(),p.getTotal(),p.getNotes(),p.getCreatedBy(),p.getOrderedAt(),
   p.getSubmittedBy(),p.getSubmittedAt(),p.getApprovedBy(),p.getApprovedAt(),p.getCancelledBy(),p.getCancelledAt(),
   p.getLines().stream().map(l->new OrderLineView(l.getId(),l.getProduct().getId(),l.getSkuSnapshot(),l.getProductNameSnapshot(),l.getOrderedQuantity(),l.getReceivedQuantity(),l.remaining(),l.getUnitCost(),l.getLineTotal())).toList());}
 }
 public record ReceiptLineView(UUID id,UUID purchaseOrderLineId,UUID productId,String sku,BigDecimal quantity,BigDecimal unitCost,BigDecimal lineTotal){}
 public record ReceiptView(UUID id,String receiptNumber,String status,UUID purchaseOrderId,String purchaseOrderNumber,UUID supplierId,UUID warehouseId,String warehouseCode,
  String createdBy,Instant receivedAt,BigDecimal total,List<ReceiptLineView> items){
  static ReceiptView from(GoodsReceipt r){BigDecimal total=r.getLines().stream().map(GoodsReceiptLine::getLineTotal).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
   return new ReceiptView(r.getId(),r.getReceiptNumber(),r.getStatus(),r.getPurchaseOrder().getId(),r.getPurchaseOrder().getOrderNumber(),r.getPurchaseOrder().getSupplier().getId(),r.getWarehouse().getId(),r.getWarehouse().getCode(),r.getCreatedBy(),r.getReceivedAt(),total,
   r.getLines().stream().map(l->new ReceiptLineView(l.getId(),l.getPurchaseOrderLine().getId(),l.getProduct().getId(),l.getProduct().getSku(),l.getQuantity(),l.getUnitCost(),l.getLineTotal())).toList());}
 }
}

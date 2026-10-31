package com.ecuapp.inventary.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SaleDtos {
 private SaleDtos(){}
 /** unitPrice is optional: when omitted the suggested price (cost plus product markup) is used; it can never be below cost. */
 public record LineRequest(@NotNull UUID productId,@NotNull @DecimalMin(value="0.001") @Digits(integer=11,fraction=3) BigDecimal quantity,
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal unitPrice){
  public LineRequest(UUID productId,BigDecimal quantity){this(productId,quantity,null);}
 }
 public record PricingLine(@NotNull UUID productId,@NotNull @DecimalMin(value="0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record PricingRequest(@NotNull UUID warehouseId,@NotEmpty @Size(max=100) List<@Valid PricingLine> items){}
 /** minimumPrice is the no-loss floor for stocked goods; it is zero for non-stocked goods and services. */
 public record PriceView(UUID productId,BigDecimal quantity,BigDecimal minimumPrice,BigDecimal suggestedPrice,BigDecimal marginPercent,String currency){}
 public record CreateRequest(@NotNull UUID warehouseId,@Size(max=160) String customerName,@Size(max=40) String customerPhone,
  @NotNull SaleChannel channel,@NotEmpty @Size(max=100) List<@Valid LineRequest> items,UUID customerId){
  public CreateRequest(UUID warehouseId,String customerName,String customerPhone,SaleChannel channel,List<LineRequest> items){
   this(warehouseId,customerName,customerPhone,channel,items,null);
  }
 }
 public record ItemView(UUID id,UUID productId,String sku,String productName,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public record View(UUID id,String saleNumber,SaleStatus status,UUID customerId,String customerName,String customerPhone,SaleChannel channel,
  UUID warehouseId,String warehouseCode,BigDecimal subtotal,BigDecimal tax,BigDecimal total,String currency,String createdBy,Instant soldAt,
  String cancellationReason,String cancellationRequestedBy,Instant cancellationRequestedAt,String cancellationApprovedBy,Instant cancellationApprovedAt,List<ItemView> items){
  static View from(Sale s){return new View(s.getId(),s.getSaleNumber(),s.getStatus(),s.getCustomer()==null?null:s.getCustomer().getId(),s.getCustomerName(),s.getCustomerPhone(),s.getChannel(),
   s.getWarehouse().getId(),s.getWarehouse().getCode(),s.getSubtotal(),s.getTax(),s.getTotal(),s.getCurrency(),s.getCreatedBy(),s.getSoldAt(),
   s.getCancellationReason(),s.getCancellationRequestedBy(),s.getCancellationRequestedAt(),s.getCancellationApprovedBy(),s.getCancellationApprovedAt(),
   s.getItems().stream().map(i->new ItemView(i.getId(),i.getProduct().getId(),i.getSkuSnapshot(),i.getProductNameSnapshot(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());}
 }
}

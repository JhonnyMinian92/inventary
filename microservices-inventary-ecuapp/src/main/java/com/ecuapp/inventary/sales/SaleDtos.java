package com.ecuapp.inventary.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SaleDtos {
 private SaleDtos(){}
 public record LineRequest(@NotNull UUID productId,@NotNull @DecimalMin(value="0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record CreateRequest(@NotNull UUID warehouseId,@Size(max=160) String customerName,@Size(max=40) String customerPhone,
  @NotNull SaleChannel channel,@NotEmpty @Size(max=100) List<@Valid LineRequest> items){}
 public record ItemView(UUID productId,String sku,String productName,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public record View(UUID id,String saleNumber,SaleStatus status,String customerName,String customerPhone,SaleChannel channel,
  UUID warehouseId,String warehouseCode,BigDecimal subtotal,BigDecimal tax,BigDecimal total,String currency,String createdBy,Instant soldAt,List<ItemView> items){
  static View from(Sale s){return new View(s.getId(),s.getSaleNumber(),s.getStatus(),s.getCustomerName(),s.getCustomerPhone(),s.getChannel(),
   s.getWarehouse().getId(),s.getWarehouse().getCode(),s.getSubtotal(),s.getTax(),s.getTotal(),s.getCurrency(),s.getCreatedBy(),s.getSoldAt(),
   s.getItems().stream().map(i->new ItemView(i.getProduct().getId(),i.getSkuSnapshot(),i.getProductNameSnapshot(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());}
 }
}

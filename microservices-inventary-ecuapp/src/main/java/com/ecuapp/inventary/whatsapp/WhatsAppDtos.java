package com.ecuapp.inventary.whatsapp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WhatsAppDtos {
 private WhatsAppDtos(){}
 public record CreateCartRequest(@NotBlank @Size(max=120) @Pattern(regexp="[A-Za-z0-9+._:@-]{6,120}",message="customerRef debe ser un identificador WhatsApp verificado") String customerRef){}
 public record CartItemRequest(@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity){}
 public record QuoteRequest(@NotNull UUID warehouseId){}
 public record CreateOrderRequest(@NotNull UUID quoteId,@AssertTrue(message="Se requiere confirmación explícita del cliente") boolean consentConfirmed){}
 public record CartLineView(UUID productId,String sku,String name,String description,BigDecimal quantity,BigDecimal unitPrice,String currency,String imageUrl,boolean available){}
 public record CartView(UUID id,String customerRef,CartStatus status,Instant createdAt,Instant updatedAt,Instant expiresAt,List<CartLineView> items){
  static CartView from(WhatsAppCart c,List<CartLineView> items){return new CartView(c.getId(),c.getCustomerRef(),c.getStatus(),c.getCreatedAt(),c.getUpdatedAt(),c.getExpiresAt(),items);}
 }
 public record QuoteLineView(UUID productId,String sku,String productName,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public record QuoteView(UUID id,String quoteNumber,QuoteStatus status,UUID cartId,UUID warehouseId,String warehouseCode,
   String currency,BigDecimal subtotal,BigDecimal tax,BigDecimal total,Instant expiresAt,Instant createdAt,List<QuoteLineView> items){
  static QuoteView from(WhatsAppQuote q){return new QuoteView(q.getId(),q.getQuoteNumber(),q.getStatus(),q.getCart().getId(),q.getWarehouse().getId(),q.getWarehouse().getCode(),
   q.getCurrency(),q.getSubtotal(),q.getTax(),q.getTotal(),q.getExpiresAt(),q.getCreatedAt(),
   q.getItems().stream().map(i->new QuoteLineView(i.getProduct().getId(),i.getSkuSnapshot(),i.getProductNameSnapshot(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());}
 }
 public record OrderLineView(UUID productId,String sku,String productName,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 public record OrderView(UUID id,String orderNumber,WhatsAppOrderStatus status,UUID cartId,UUID quoteId,String customerRef,UUID warehouseId,String warehouseCode,
  String currency,BigDecimal subtotal,BigDecimal tax,BigDecimal total,Instant expiresAt,Instant createdAt,Instant updatedAt,List<OrderLineView> items){
  static OrderView from(WhatsAppOrder o){return new OrderView(o.getId(),o.getOrderNumber(),o.getStatus(),o.getCart().getId(),o.getQuote().getId(),o.getCustomerRef(),o.getWarehouse().getId(),o.getWarehouse().getCode(),
   o.getCurrency(),o.getSubtotal(),o.getTax(),o.getTotal(),o.getExpiresAt(),o.getCreatedAt(),o.getUpdatedAt(),
   o.getItems().stream().map(i->new OrderLineView(i.getProduct().getId(),i.getSkuSnapshot(),i.getProductNameSnapshot(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());}
 }
}

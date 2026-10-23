package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.catalog.ProductService;
import com.ecuapp.inventary.catalog.ProductDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp")
public class WhatsAppIntegrationController {
 private final WhatsAppIntegrationKey keyService;private final WhatsAppCommerceService commerce;private final ProductService products;
 public WhatsAppIntegrationController(WhatsAppIntegrationKey keyService,WhatsAppCommerceService commerce,ProductService products){this.keyService=keyService;this.commerce=commerce;this.products=products;}
 @GetMapping("/catalog/products")
 public org.springframework.data.domain.Page<ProductDtos.PublicView> catalog(@RequestHeader(name="X-Integration-Key",required=false) String key,
   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  keyService.requireValid(key);if(page<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be >= 0");
  return products.publicList(PageRequest.of(page,Math.min(Math.max(size,1),100),Sort.by(Sort.Direction.ASC,"name")));
 }
 @GetMapping("/catalog/products/{identifier}")
 public ProductDtos.PublicView product(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable String identifier){
  keyService.requireValid(key);return products.getPublic(identifier);
 }
 @PostMapping("/carts")
 public WhatsAppDtos.CartView createCart(@RequestHeader(name="X-Integration-Key",required=false) String key,@Valid @RequestBody WhatsAppDtos.CreateCartRequest request){
  keyService.requireValid(key);return commerce.createCart(request.customerRef(),"whatsapp-integration");
 }
 @GetMapping("/carts/{cartId}")
 public WhatsAppDtos.CartView getCart(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID cartId){
  keyService.requireValid(key);return commerce.getCart(cartId);
 }
 @PutMapping("/carts/{cartId}/items/{productId}")
 public WhatsAppDtos.CartView setItem(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID cartId,@PathVariable UUID productId,
  @Valid @RequestBody WhatsAppDtos.CartItemRequest request){keyService.requireValid(key);return commerce.setItem(cartId,productId,request,"whatsapp-integration");}
 @DeleteMapping("/carts/{cartId}/items/{productId}")
 public WhatsAppDtos.CartView removeItem(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID cartId,@PathVariable UUID productId){
  keyService.requireValid(key);return commerce.removeItem(cartId,productId,"whatsapp-integration");
 }
 @PostMapping("/carts/{cartId}/quotes")
 public WhatsAppDtos.QuoteView quote(@RequestHeader(name="X-Integration-Key",required=false) String key,@RequestHeader("Idempotency-Key") String idempotencyKey,
  @PathVariable UUID cartId,@Valid @RequestBody WhatsAppDtos.QuoteRequest request){keyService.requireValid(key);return commerce.createQuote(cartId,idempotencyKey,request,"whatsapp-integration");}
 @GetMapping("/quotes/{quoteId}")
 public WhatsAppDtos.QuoteView getQuote(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID quoteId){keyService.requireValid(key);return commerce.getQuote(quoteId);}
 @PostMapping("/orders")
 public WhatsAppDtos.OrderView createOrder(@RequestHeader(name="X-Integration-Key",required=false) String key,@RequestHeader("Idempotency-Key") String idempotencyKey,
  @Valid @RequestBody WhatsAppDtos.CreateOrderRequest request){keyService.requireValid(key);return commerce.createOrder(idempotencyKey,request,"whatsapp-integration");}
 @GetMapping("/orders/{orderId}")
 public WhatsAppDtos.OrderView getOrder(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID orderId){keyService.requireValid(key);return commerce.getOrder(orderId);}
 @PostMapping("/orders/{orderId}/cancel")
 public WhatsAppDtos.OrderView cancelOrder(@RequestHeader(name="X-Integration-Key",required=false) String key,@PathVariable UUID orderId){keyService.requireValid(key);return commerce.cancelOrder(orderId,"whatsapp-integration");}
}

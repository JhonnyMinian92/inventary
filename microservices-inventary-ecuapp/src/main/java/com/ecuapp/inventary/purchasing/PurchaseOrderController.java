package com.ecuapp.inventary.purchasing;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class PurchaseOrderController {
 private final PurchaseOrderService service;
 public PurchaseOrderController(PurchaseOrderService service){this.service=service;}
 @GetMapping("/api/v1/purchase-orders") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public Page<PurchaseDtos.OrderView> list(@PageableDefault(size=20,sort="orderedAt,desc") Pageable p){return service.list(p);}
 @GetMapping("/api/v1/purchase-orders/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public PurchaseDtos.OrderView get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/api/v1/purchase-orders") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PurchaseDtos.OrderView create(@Valid @RequestBody PurchaseDtos.CreateOrderRequest r,Authentication auth){return service.create(r,auth.getName());}
 @PostMapping("/api/v1/purchase-orders/{id}/submit") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PurchaseDtos.OrderView submit(@PathVariable UUID id){return service.submit(id);}
 @PostMapping("/api/v1/purchase-orders/{id}/approve") @PreAuthorize("hasRole('ADMIN')")
 public PurchaseDtos.OrderView approve(@PathVariable UUID id){return service.approve(id);}
 @PostMapping("/api/v1/purchase-orders/{id}/cancel") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PurchaseDtos.OrderView cancel(@PathVariable UUID id){return service.cancel(id);}
 @PostMapping("/api/v1/purchase-orders/{id}/receipts") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PurchaseDtos.ReceiptView receive(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PurchaseDtos.ReceiptRequest r,Authentication auth){return service.receive(id,key,r,auth.getName());}
 @GetMapping("/api/v1/goods-receipts/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public PurchaseDtos.ReceiptView getReceipt(@PathVariable UUID id){return service.getReceipt(id);}
}

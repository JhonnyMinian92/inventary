package com.ecuapp.inventary.purchasing;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {
 private final PurchaseReturnService service;
 public PurchaseReturnController(PurchaseReturnService service){this.service=service;}
 @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public PurchaseReturnDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PurchaseReturnDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PurchaseReturnDtos.CreateRequest request,Authentication auth){return service.create(key,request,auth.getName());}
}

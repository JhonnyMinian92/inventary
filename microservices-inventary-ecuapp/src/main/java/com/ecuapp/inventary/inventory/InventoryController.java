package com.ecuapp.inventary.inventory;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/balances") public Page<InventoryDtos.BalanceView> balances(@PageableDefault(size=20) Pageable p){return service.balances(p);}
 @GetMapping("/balances/{productId}/{warehouseId}") public InventoryDtos.BalanceView balance(@PathVariable UUID productId,@PathVariable UUID warehouseId){return service.balance(productId,warehouseId);}
 @GetMapping("/movements") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public Page<InventoryDtos.MovementView> movements(@PageableDefault(size=20) Pageable p){return service.movements(p);}
 @PostMapping("/adjustments") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public InventoryDtos.MovementView adjust(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody InventoryDtos.AdjustmentRequest r,Authentication auth){return service.adjust(key,r,auth.getName());}
}

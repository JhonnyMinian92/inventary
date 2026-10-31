package com.ecuapp.inventary.inventory;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/balances") public Page<InventoryDtos.BalanceView> balances(@RequestParam(required=false) UUID productId,@RequestParam(required=false) UUID warehouseId,
  @RequestParam(defaultValue="false") boolean onlyAvailable,@PageableDefault(size=20) Pageable p,Authentication auth){return service.balances(productId,warehouseId,onlyAvailable,p,auth.getName());}
 @GetMapping("/balances/{productId}/{warehouseId}") public InventoryDtos.BalanceView balance(@PathVariable UUID productId,@PathVariable UUID warehouseId,Authentication auth){return service.balance(productId,warehouseId,auth.getName());}
 /** Informative availability check; it never reserves stock. */
 @GetMapping("/availability") public InventoryDtos.AvailabilityView availability(@RequestParam(required=false) UUID productId,@RequestParam(required=false) String sku,
  @RequestParam UUID warehouseId,@RequestParam(required=false) BigDecimal quantity,Authentication auth){return service.availability(productId,sku,warehouseId,quantity,auth.getName());}
 @GetMapping("/movements") @PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
 public Page<InventoryDtos.MovementView> movements(@RequestParam(required=false) UUID productId,@RequestParam(required=false) UUID warehouseId,
  @RequestParam(required=false) MovementType type,@RequestParam(required=false) String referenceType,@RequestParam(required=false) UUID referenceId,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @PageableDefault(size=20) Pageable p,Authentication auth){return service.movements(productId,warehouseId,type,referenceType,referenceId,from,to,p,auth.getName());}
 @GetMapping("/movements/{id}") @PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
 public InventoryDtos.MovementDetailView movement(@PathVariable UUID id,Authentication auth){return service.movement(id,auth.getName());}
 @PostMapping("/adjustments") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasAuthority('inventory:manage')")
 public InventoryDtos.MovementView adjust(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody InventoryDtos.AdjustmentRequest r,Authentication auth){return service.adjust(key,r,auth.getName());}
}

package com.ecuapp.inventary.transfers;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/transfers")
@PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
public class StockTransferController {
 private final StockTransferService service;
 public StockTransferController(StockTransferService service){this.service=service;}
 @GetMapping public Page<TransferDtos.View> list(@RequestParam(required=false) TransferStatus status,@RequestParam(required=false) UUID sourceWarehouseId,
  @RequestParam(required=false) UUID destinationWarehouseId,@PageableDefault(size=20) Pageable p,Authentication auth){return service.list(status,sourceWarehouseId,destinationWarehouseId,p,auth.getName());}
 @GetMapping("/{id}") public TransferDtos.View get(@PathVariable UUID id,Authentication auth){return service.get(id,auth.getName());}
 @PostMapping @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferDtos.CreateRequest r,Authentication a){return service.create(key,r,a.getName());}
 @PostMapping("/{id}/dispatch") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View dispatch(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,Authentication a){return service.dispatch(id,key,a.getName());}
 @PostMapping("/{id}/receive") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View receive(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,Authentication a){return service.receive(id,key,a.getName());}
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View cancel(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferDtos.ReasonRequest request,Authentication a){return service.cancel(id,key,request.reason(),a.getName());}
 @PostMapping("/{id}/reverse") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View reverse(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferDtos.ReasonRequest request,Authentication a){return service.reverse(id,key,request.reason(),a.getName());}
}

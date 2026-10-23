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
 @GetMapping public Page<TransferDtos.View> list(@PageableDefault(size=20,sort="createdAt,desc") Pageable p){return service.list(p);}
 @GetMapping("/{id}") public TransferDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferDtos.CreateRequest r,Authentication a){return service.create(key,r,a.getName());}
 @PostMapping("/{id}/dispatch") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View dispatch(@PathVariable UUID id,Authentication a){return service.dispatch(id,a.getName());}
 @PostMapping("/{id}/receive") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View receive(@PathVariable UUID id,Authentication a){return service.receive(id,a.getName());}
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('inventory:manage')")
 public TransferDtos.View cancel(@PathVariable UUID id,Authentication a){return service.cancel(id,a.getName());}
}

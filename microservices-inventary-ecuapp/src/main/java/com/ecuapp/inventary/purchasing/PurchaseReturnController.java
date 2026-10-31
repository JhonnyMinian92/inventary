package com.ecuapp.inventary.purchasing;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {
 private final PurchaseReturnService service;
 public PurchaseReturnController(PurchaseReturnService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('purchasing:read')")
 public Page<PurchaseReturnDtos.View> list(@RequestParam(required=false) UUID goodsReceiptId,@RequestParam(required=false) UUID warehouseId,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @PageableDefault(size=20) Pageable p,Authentication auth){return service.list(goodsReceiptId,warehouseId,from,to,p,auth.getName());}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('purchasing:read')")
 public PurchaseReturnDtos.View get(@PathVariable UUID id,Authentication auth){return service.get(id,auth.getName());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('purchasing:manage')")
 public PurchaseReturnDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PurchaseReturnDtos.CreateRequest request,Authentication auth){return service.create(key,request,auth.getName());}
}

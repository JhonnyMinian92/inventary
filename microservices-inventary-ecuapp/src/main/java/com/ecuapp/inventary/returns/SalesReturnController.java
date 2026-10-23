package com.ecuapp.inventary.returns;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/sales-returns")
public class SalesReturnController {
 private final SalesReturnService service;
 public SalesReturnController(SalesReturnService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('sales:read')")
 public org.springframework.data.domain.Page<SalesReturnDtos.View> list(@RequestParam(required=false) UUID sourceSaleId,@RequestParam(required=false) UUID warehouseId,
  @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
  @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
  @org.springframework.data.web.PageableDefault(size=20) org.springframework.data.domain.Pageable p){return service.list(sourceSaleId,warehouseId,from,to,p);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:read')")
 public SalesReturnDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasAuthority('sales:manage')")
 public SalesReturnDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SalesReturnDtos.CreateRequest request,Authentication auth){return service.create(key,request,auth.getName());}
}

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
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:read')")
 public SalesReturnDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasAuthority('sales:manage')")
 public SalesReturnDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SalesReturnDtos.CreateRequest request,Authentication auth){return service.create(key,request,auth.getName());}
}

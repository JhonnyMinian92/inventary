package com.ecuapp.inventary.sales;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/sales")
public class SalesController {
 private final SaleService service;
 public SalesController(SaleService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('sales:read')")
 public Page<SaleDtos.View> list(@PageableDefault(size=20,sort="soldAt,desc") Pageable p){return service.list(p);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:read')")
 public SaleDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('sales:manage')")
 public SaleDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SaleDtos.CreateRequest request,Authentication auth){return service.create(key,request,auth.getName());}
 // A seller cannot approve cancellation of a completed sale.
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('sales:cancel')")
 public SaleDtos.View cancel(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CancelRequest request,Authentication auth){return service.cancel(id,key,request.reason(),auth.getName());}
 public record CancelRequest(@NotBlank @Size(max=1000) String reason){}
}

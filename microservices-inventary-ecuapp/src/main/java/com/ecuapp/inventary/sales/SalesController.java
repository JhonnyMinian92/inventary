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
 private final SaleService service;private final SalePricingService pricing;
 public SalesController(SaleService service,SalePricingService pricing){this.service=service;this.pricing=pricing;}
 /** Read-only: suggested and minimum (no-loss) prices for the lines of a sale being prepared. */
 @PostMapping("/pricing") @PreAuthorize("hasAuthority('sales:manage')")
 public java.util.List<SaleDtos.PriceView> pricing(@Valid @RequestBody SaleDtos.PricingRequest request){return pricing.quote(request);}
 @GetMapping @PreAuthorize("hasAuthority('sales:read')")
 public Page<SaleDtos.View> list(@RequestParam(required=false) SaleStatus status,@RequestParam(required=false) UUID warehouseId,
  @RequestParam(required=false) UUID customerId,@RequestParam(required=false) SaleChannel channel,
  @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
  @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
  @PageableDefault(size=20) Pageable p){return service.list(status,warehouseId,customerId,channel,from,to,p);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:read')")
 public SaleDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('sales:manage')")
 public SaleDtos.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SaleDtos.CreateRequest request,Authentication auth){
  if(request.channel()==SaleChannel.SUBSCRIPTION)throw new com.ecuapp.inventary.common.BusinessRuleException("Las facturas de suscripción las genera el sistema; registre la suscripción en Suscripciones.");
  return service.create(key,request,auth.getName());
 }
 @PostMapping("/{id}/request-cancellation") @PreAuthorize("hasAuthority('sales:manage')")
 public SaleDtos.View requestCancellation(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CancelRequest request,Authentication auth){return service.requestCancellation(id,key,request.reason(),auth.getName());}
 @PostMapping("/{id}/approve-cancellation") @PreAuthorize("hasAuthority('sales:cancel')")
 public SaleDtos.View approveCancellation(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,Authentication auth){return service.approveCancellation(id,key,auth.getName());}
 public record CancelRequest(@NotBlank @Size(max=1000) String reason){}
}

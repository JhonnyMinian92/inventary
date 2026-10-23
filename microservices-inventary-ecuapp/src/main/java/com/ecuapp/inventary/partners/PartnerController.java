package com.ecuapp.inventary.partners;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class PartnerController {
 private final PartnerService service;
 public PartnerController(PartnerService service){this.service=service;}

 @GetMapping("/api/v1/suppliers") public Page<PartnerDtos.View> suppliers(@PageableDefault(size=20,sort="name") Pageable p){return service.list(PartnerType.SUPPLIER,p);}
 @GetMapping("/api/v1/suppliers/{id}") public PartnerDtos.View supplier(@PathVariable UUID id){return service.get(PartnerType.SUPPLIER,id);}
 @PostMapping("/api/v1/suppliers") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PartnerDtos.View createSupplier(@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.create(PartnerType.SUPPLIER,r,a.getName());}
 @PutMapping("/api/v1/suppliers/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public PartnerDtos.View updateSupplier(@PathVariable UUID id,@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.update(PartnerType.SUPPLIER,id,r,a.getName());}
 @PostMapping("/api/v1/suppliers/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivateSupplier(@PathVariable UUID id,Authentication a){service.deactivate(PartnerType.SUPPLIER,id,a.getName());}

 @GetMapping("/api/v1/customers") public Page<PartnerDtos.View> customers(@PageableDefault(size=20,sort="name") Pageable p){return service.list(PartnerType.CUSTOMER,p);}
 @GetMapping("/api/v1/customers/{id}") public PartnerDtos.View customer(@PathVariable UUID id){return service.get(PartnerType.CUSTOMER,id);}
 @PostMapping("/api/v1/customers") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','SALES')")
 public PartnerDtos.View createCustomer(@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.create(PartnerType.CUSTOMER,r,a.getName());}
 @PutMapping("/api/v1/customers/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','SALES')")
 public PartnerDtos.View updateCustomer(@PathVariable UUID id,@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.update(PartnerType.CUSTOMER,id,r,a.getName());}
 @PostMapping("/api/v1/customers/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivateCustomer(@PathVariable UUID id,Authentication a){service.deactivate(PartnerType.CUSTOMER,id,a.getName());}
}

package com.ecuapp.inventary.partners;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * Access policy: suppliers are part of purchasing (read: purchasing:read, write: partners:manage);
 * customers are read with partners:read and maintained with customers:manage.
 */
@RestController
public class PartnerController {
 private final PartnerService service;
 public PartnerController(PartnerService service){this.service=service;}

 @GetMapping("/api/v1/suppliers") @PreAuthorize("hasAuthority('purchasing:read')")
 public Page<PartnerDtos.View> suppliers(@RequestParam(required=false) String q,@RequestParam(required=false) Boolean active,@PageableDefault(size=20,sort="name") Pageable p){return service.list(PartnerType.SUPPLIER,q,active,p);}
 @GetMapping("/api/v1/suppliers/{id}") @PreAuthorize("hasAuthority('purchasing:read')") public PartnerDtos.View supplier(@PathVariable UUID id){return service.get(PartnerType.SUPPLIER,id);}
 @PostMapping("/api/v1/suppliers") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('partners:manage')")
 public PartnerDtos.View createSupplier(@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.create(PartnerType.SUPPLIER,r,a.getName());}
 @PutMapping("/api/v1/suppliers/{id}") @PreAuthorize("hasAuthority('partners:manage')")
 public PartnerDtos.View updateSupplier(@PathVariable UUID id,@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.update(PartnerType.SUPPLIER,id,r,a.getName());}
 @PostMapping("/api/v1/suppliers/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('partners:manage')")
 public void deactivateSupplier(@PathVariable UUID id,Authentication a){service.deactivate(PartnerType.SUPPLIER,id,a.getName());}
 @DeleteMapping("/api/v1/suppliers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('partners:manage')")
 public void deleteSupplier(@PathVariable UUID id,Authentication a){service.delete(PartnerType.SUPPLIER,id,a.getName());}
 @PostMapping("/api/v1/suppliers/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('partners:manage')")
 public void activateSupplier(@PathVariable UUID id,Authentication a){service.activate(PartnerType.SUPPLIER,id,a.getName());}

 @GetMapping("/api/v1/customers") @PreAuthorize("hasAuthority('partners:read')")
 public Page<PartnerDtos.View> customers(@RequestParam(required=false) String q,@RequestParam(required=false) Boolean active,@PageableDefault(size=20,sort="name") Pageable p){return service.list(PartnerType.CUSTOMER,q,active,p);}
 @GetMapping("/api/v1/customers/{id}") @PreAuthorize("hasAuthority('partners:read')") public PartnerDtos.View customer(@PathVariable UUID id){return service.get(PartnerType.CUSTOMER,id);}
 @PostMapping("/api/v1/customers") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('customers:manage')")
 public PartnerDtos.View createCustomer(@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.create(PartnerType.CUSTOMER,r,a.getName());}
 @PutMapping("/api/v1/customers/{id}") @PreAuthorize("hasAuthority('customers:manage')")
 public PartnerDtos.View updateCustomer(@PathVariable UUID id,@Valid @RequestBody PartnerDtos.WriteRequest r,Authentication a){return service.update(PartnerType.CUSTOMER,id,r,a.getName());}
 @PostMapping("/api/v1/customers/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('customers:manage')")
 public void deactivateCustomer(@PathVariable UUID id,Authentication a){service.deactivate(PartnerType.CUSTOMER,id,a.getName());}
 @DeleteMapping("/api/v1/customers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('customers:manage')")
 public void deleteCustomer(@PathVariable UUID id,Authentication a){service.delete(PartnerType.CUSTOMER,id,a.getName());}
 @PostMapping("/api/v1/customers/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('customers:manage')")
 public void activateCustomer(@PathVariable UUID id,Authentication a){service.activate(PartnerType.CUSTOMER,id,a.getName());}
}

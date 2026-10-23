package com.ecuapp.inventary.catalog;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {
 private final ProductService service;
 public ProductController(ProductService service){this.service=service;}
 @GetMapping public Page<ProductDtos.View> list(@RequestParam(required=false) String q,@PageableDefault(size=20,sort="name") Pageable p){return service.list(q,p);}
 @GetMapping("/{id}") public ProductDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ProductDtos.View create(@Valid @RequestBody ProductDtos.WriteRequest r){return service.create(r);}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ProductDtos.View update(@PathVariable UUID id,@Valid @RequestBody ProductDtos.WriteRequest r){return service.update(id,r);}
 @PostMapping("/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivate(@PathVariable UUID id){service.deactivate(id);}
}

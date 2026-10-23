package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.application.ProductCommand;
import com.ecuapp.inventary.catalog.application.ProductUseCases;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {
 private final ProductUseCases products;
 public ProductController(ProductUseCases products){this.products=products;}
 @GetMapping public Page<ProductDtos.View> list(@RequestParam(required=false) String q,@PageableDefault(size=20,sort="name") Pageable p){return products.listProducts(q,p).map(ProductDtos.View::from);}
 @GetMapping("/{id}") public ProductDtos.View get(@PathVariable UUID id){return ProductDtos.View.from(products.getProduct(id));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ProductDtos.View create(@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.createProduct(command(r),auth.getName()));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ProductDtos.View update(@PathVariable UUID id,@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.updateProduct(id,command(r),auth.getName()));}
 @PostMapping("/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivate(@PathVariable UUID id,Authentication auth){products.deactivateProduct(id,auth.getName());}
 private ProductCommand command(ProductDtos.WriteRequest r){return new ProductCommand(r.sku(),r.name(),r.description(),r.categoryId(),r.costPrice(),r.salePrice(),r.currency(),r.imageUrl());}
}

package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;
import com.ecuapp.inventary.catalog.application.ProductCommand;
import com.ecuapp.inventary.catalog.application.ProductUseCases;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Set;
import com.ecuapp.inventary.common.InvalidQueryException;

@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {
 private static final Set<String> ALLOWED_SORT_PROPERTIES=Set.of("name","sku","createdAt","updatedAt","active","salePrice");
 private final ProductUseCases products;
 public ProductController(ProductUseCases products){this.products=products;}
 @GetMapping public Page<ProductDtos.View> list(@RequestParam(required=false) String q,@PageableDefault(size=20,sort="name") Pageable p){PageResult<com.ecuapp.inventary.catalog.application.ProductResult> result=products.listProducts(q,pageQuery(p));return new PageImpl<>(result.content().stream().map(ProductDtos.View::from).toList(),PageRequest.of(result.page(),result.size(),p.getSort()),result.totalElements());}
 @GetMapping("/{id}") public ProductDtos.View get(@PathVariable UUID id){return ProductDtos.View.from(products.getProduct(id));}
 @PostMapping @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductDtos.View create(@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.createProduct(command(r),auth.getName()));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductDtos.View update(@PathVariable UUID id,@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.updateProduct(id,command(r),auth.getName()));}
 @PostMapping("/{id}/deactivate") @PreAuthorize("hasAuthority('catalog:manage')")
 public void deactivate(@PathVariable UUID id,Authentication auth){products.deactivateProduct(id,auth.getName());}
 private PageQuery pageQuery(Pageable pageable){
  var terms=pageable.getSort().stream().map(order->{
   if(!ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))throw new InvalidQueryException("Propiedad de ordenamiento no permitida: "+order.getProperty()+".");
   return new PageQuery.SortTerm(order.getProperty(),order.isDescending()?PageQuery.Direction.DESC:PageQuery.Direction.ASC);
  }).toList();
  return new PageQuery(pageable.getPageNumber(),pageable.getPageSize(),terms);
 }
 private ProductCommand command(ProductDtos.WriteRequest r){return new ProductCommand(r.sku(),r.name(),r.description(),r.categoryId(),r.costPrice(),r.salePrice(),r.currency(),r.imageUrl());}
}

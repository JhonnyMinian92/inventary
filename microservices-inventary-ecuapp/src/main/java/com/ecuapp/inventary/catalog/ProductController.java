package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.application.PageQuery;
import com.ecuapp.inventary.catalog.application.PageResult;
import com.ecuapp.inventary.catalog.application.ProductCommand;
import com.ecuapp.inventary.catalog.application.ProductResult;
import com.ecuapp.inventary.catalog.application.ProductUseCases;
import com.ecuapp.inventary.common.Pages;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {
 private static final Set<String> ALLOWED_SORT_PROPERTIES=Set.of("name","sku","createdAt","updatedAt","active","salePrice");
 private final ProductUseCases products;
 public ProductController(ProductUseCases products){this.products=products;}
 @GetMapping public Page<ProductDtos.View> list(@RequestParam(required=false) String q,@RequestParam(required=false) UUID categoryId,
  @RequestParam(required=false) Boolean active,@RequestParam(required=false) ProductKind kind,
  @PageableDefault(size=20,sort="name") Pageable p,Authentication auth){
  Pageable safe=Pages.of(p,ALLOWED_SORT_PROPERTIES,Sort.by("name"));
  PageResult<ProductResult> result=products.listProducts(q,categoryId,active,kind,pageQuery(safe));
  boolean cost=canSeeCost(auth);
  return new PageImpl<>(result.content().stream().map(r->ProductDtos.View.from(r,cost)).toList(),PageRequest.of(result.page(),result.size(),safe.getSort()),result.totalElements());
 }
 @GetMapping("/{id}") public ProductDtos.View get(@PathVariable UUID id,Authentication auth){return ProductDtos.View.from(products.getProduct(id),canSeeCost(auth));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductDtos.View create(@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.createProduct(command(r),auth.getName()),true);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductDtos.View update(@PathVariable UUID id,@Valid @RequestBody ProductDtos.WriteRequest r,Authentication auth){return ProductDtos.View.from(products.updateProduct(id,command(r),auth.getName()),true);}
 @PostMapping("/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void deactivate(@PathVariable UUID id,Authentication auth){products.deactivateProduct(id,auth.getName());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void delete(@PathVariable UUID id,Authentication auth){products.deleteProduct(id,auth.getName());}
 @PostMapping("/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void activate(@PathVariable UUID id,Authentication auth){products.activateProduct(id,auth.getName());}
 private boolean canSeeCost(Authentication auth){
  return auth!=null&&auth.getAuthorities().stream().anyMatch(a->"catalog:manage".equals(a.getAuthority())||"reports:financial".equals(a.getAuthority()));
 }
 private PageQuery pageQuery(Pageable pageable){
  var terms=pageable.getSort().stream().map(order->new PageQuery.SortTerm(order.getProperty(),order.isDescending()?PageQuery.Direction.DESC:PageQuery.Direction.ASC)).toList();
  return new PageQuery(pageable.getPageNumber(),pageable.getPageSize(),terms);
 }
 private ProductCommand command(ProductDtos.WriteRequest r){return new ProductCommand(r.sku(),r.name(),r.description(),r.categoryId(),r.costPrice(),r.salePrice(),r.currency(),r.imageUrl(),r.kind(),r.unitOfMeasureId(),r.attributes());}
}

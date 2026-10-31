package com.ecuapp.inventary.catalog.api.rest.controller;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.catalog.application.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** Anonymous catalog: only active products of active categories, never costs or margins. */
@RestController
@RequestMapping("/api/v1/public/catalog")
public class PublicCatalogController {
 private final ProductService service;
 public PublicCatalogController(ProductService service){this.service=service;}
 @GetMapping("/products")
 public Page<ProductDtos.PublicView> products(@RequestParam(required=false) String q,@RequestParam(required=false) UUID categoryId,
  @PageableDefault(size=20,sort="name") Pageable pageable){return service.publicList(q,categoryId,pageable);}
 @GetMapping("/products/{identifier}")
 public ProductDtos.PublicDetailView product(@PathVariable String identifier){return service.getPublicDetail(identifier);}
 @GetMapping("/categories")
 public Page<ProductDtos.PublicCategoryView> categories(@PageableDefault(size=50,sort="name") Pageable pageable){return service.publicCategories(pageable);}
}

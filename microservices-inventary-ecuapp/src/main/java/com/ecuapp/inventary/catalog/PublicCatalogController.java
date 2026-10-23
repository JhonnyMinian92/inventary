package com.ecuapp.inventary.catalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public/catalog")
public class PublicCatalogController {
 private final ProductService service;
 public PublicCatalogController(ProductService service){this.service=service;}
 @GetMapping("/products")
 public Page<ProductDtos.PublicView> products(@PageableDefault(size=20,sort="name") Pageable pageable){return service.publicList(pageable);}
}

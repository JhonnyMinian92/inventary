package com.ecuapp.inventary.media;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/products/{productId}/images")
public class ProductImageController {
 private final ProductImageService service;
 public ProductImageController(ProductImageService service){this.service=service;}
 @GetMapping public List<ProductImageDtos.View> list(@PathVariable UUID productId){return service.list(productId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductImageDtos.View create(@PathVariable UUID productId,@Valid @RequestBody ProductImageDtos.CreateRequest r,Authentication a){return service.create(productId,r,a.getName());}
 @PatchMapping("/{imageId}") @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductImageDtos.View update(@PathVariable UUID productId,@PathVariable UUID imageId,@Valid @RequestBody ProductImageDtos.UpdateRequest r,Authentication a){return service.update(productId,imageId,r,a.getName());}
 @DeleteMapping("/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void unpublish(@PathVariable UUID productId,@PathVariable UUID imageId,Authentication a){service.unpublish(productId,imageId,a.getName());}
}

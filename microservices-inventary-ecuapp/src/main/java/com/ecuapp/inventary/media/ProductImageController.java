package com.ecuapp.inventary.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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
 @PostMapping(path="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductImageDtos.View upload(@PathVariable UUID productId,@RequestPart("file") MultipartFile file,
  @RequestParam(required=false) String altText,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int sortOrder,
  @RequestParam(defaultValue="false") boolean primaryImage,Authentication a){
  return service.upload(productId,file,altText,sortOrder,primaryImage,a.getName());
 }
 @PatchMapping("/{imageId}") @PreAuthorize("hasAuthority('catalog:manage')")
 public ProductImageDtos.View update(@PathVariable UUID productId,@PathVariable UUID imageId,@Valid @RequestBody ProductImageDtos.UpdateRequest r,Authentication a){return service.update(productId,imageId,r,a.getName());}
 @DeleteMapping("/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void unpublish(@PathVariable UUID productId,@PathVariable UUID imageId,Authentication a){service.unpublish(productId,imageId,a.getName());}
}

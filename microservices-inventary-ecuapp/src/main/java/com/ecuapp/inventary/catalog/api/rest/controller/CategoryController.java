package com.ecuapp.inventary.catalog.api.rest.controller;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.catalog.api.rest.dto.*;
import com.ecuapp.inventary.catalog.application.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {
 private final CategoryService service;private final CatalogStructureService structure;
 public CategoryController(CategoryService service,CatalogStructureService structure){this.service=service;this.structure=structure;}
 @GetMapping public Page<CategoryDtos.View> list(@RequestParam(required=false) Boolean active,@PageableDefault(size=20,sort="name") Pageable pageable){return service.list(active,pageable);}
 @GetMapping("/{id}") public CategoryDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('catalog:manage')")
 public CategoryDtos.View create(@Valid @RequestBody CategoryDtos.WriteRequest request,Authentication auth){return service.create(request,auth.getName());}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('catalog:manage')")
 public CategoryDtos.View update(@PathVariable UUID id,@Valid @RequestBody CategoryDtos.WriteRequest request,Authentication auth){return service.update(id,request,auth.getName());}
 @PostMapping("/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void deactivate(@PathVariable UUID id,Authentication auth){service.deactivate(id,auth.getName());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth.getName());}
 @PostMapping("/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('catalog:manage')")
 public void activate(@PathVariable UUID id,Authentication auth){service.activate(id,auth.getName());}
 @GetMapping("/{id}/attributes") public CatalogStructureDtos.AttributeSchemaView attributes(@PathVariable UUID id){return structure.schema(id);}
 /** Publishes a new version of the attribute template; previous versions are retired, never deleted. */
 @PutMapping("/{id}/attributes") @PreAuthorize("hasAuthority('catalog:manage')")
 public CatalogStructureDtos.AttributeSchemaView replaceAttributes(@PathVariable UUID id,@Valid @RequestBody CatalogStructureDtos.AttributeSchemaRequest request,Authentication auth){
  return structure.replaceSchema(id,request,auth.getName());
 }
}

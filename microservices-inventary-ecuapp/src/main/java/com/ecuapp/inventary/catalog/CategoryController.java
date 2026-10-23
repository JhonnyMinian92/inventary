package com.ecuapp.inventary.catalog;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {
 private final CategoryService service;
 public CategoryController(CategoryService service){this.service=service;}
 @GetMapping public Page<CategoryDtos.View> list(@PageableDefault(size=20,sort="name") Pageable pageable){return service.list(pageable);}
 @GetMapping("/{id}") public CategoryDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public CategoryDtos.View create(@Valid @RequestBody CategoryDtos.WriteRequest request){return service.create(request);}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public CategoryDtos.View update(@PathVariable UUID id,@Valid @RequestBody CategoryDtos.WriteRequest request){return service.update(id,request);}
 @PostMapping("/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivate(@PathVariable UUID id){service.deactivate(id);}
}

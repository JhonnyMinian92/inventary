package com.ecuapp.inventary.warehouse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {
 private final WarehouseService service;
 public WarehouseController(WarehouseService service){this.service=service;}
 @GetMapping public Page<WarehouseDtos.View> list(@PageableDefault(size=20,sort="name") Pageable p){return service.list(p);}
 @GetMapping("/{id}") public WarehouseDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public WarehouseDtos.View create(@Valid @RequestBody WarehouseDtos.WriteRequest r){return service.create(r);}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public WarehouseDtos.View update(@PathVariable UUID id,@Valid @RequestBody WarehouseDtos.WriteRequest r){return service.update(id,r);}
 @PostMapping("/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivate(@PathVariable UUID id){service.deactivate(id);}
}

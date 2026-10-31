package com.ecuapp.inventary.warehouse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {
 private final WarehouseService service;
 public WarehouseController(WarehouseService service){this.service=service;}
 @GetMapping public Page<WarehouseDtos.View> list(@RequestParam(required=false) Boolean active,@PageableDefault(size=20,sort="name") Pageable p,Authentication auth){return service.list(active,p,auth.getName());}
 @GetMapping("/{id}") public WarehouseDtos.View get(@PathVariable UUID id,Authentication auth){return service.get(id,auth.getName());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('inventory:manage')")
 public WarehouseDtos.View create(@Valid @RequestBody WarehouseDtos.WriteRequest r,Authentication auth){return service.create(r,auth.getName());}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('inventory:manage')")
 public WarehouseDtos.View update(@PathVariable UUID id,@Valid @RequestBody WarehouseDtos.WriteRequest r,Authentication auth){return service.update(id,r,auth.getName());}
 @PostMapping("/{id}/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('inventory:manage')")
 public void deactivate(@PathVariable UUID id,Authentication auth){service.deactivate(id,auth.getName());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('inventory:manage')")
 public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth.getName());}
 @PostMapping("/{id}/activate") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('inventory:manage')")
 public void activate(@PathVariable UUID id,Authentication auth){service.activate(id,auth.getName());}
 /** Stock is kept in the MAIN location; extra locations are registered for layout and future multi-bin operations. */
 @GetMapping("/{id}/locations") public List<WarehouseDtos.LocationView> locations(@PathVariable UUID id,Authentication auth){return service.locations(id,auth.getName());}
 @PostMapping("/{id}/locations") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('inventory:manage')")
 public WarehouseDtos.LocationView createLocation(@PathVariable UUID id,@Valid @RequestBody WarehouseDtos.LocationRequest r,Authentication auth){return service.createLocation(id,r,auth.getName());}
}

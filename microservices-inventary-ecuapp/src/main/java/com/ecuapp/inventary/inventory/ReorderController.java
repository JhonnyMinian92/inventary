package com.ecuapp.inventary.inventory;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/inventory")
public class ReorderController {
 private final ReorderService service;
 public ReorderController(ReorderService service){this.service=service;}
 @GetMapping("/reorder-rules") @PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
 public List<ReorderDtos.View> list(@RequestParam(required=false) UUID warehouseId,@RequestParam(required=false) Boolean active,Authentication auth){return service.list(warehouseId,active,auth.getName());}
 @PostMapping("/reorder-rules") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasAuthority('inventory:manage')")
 public ReorderDtos.View create(@Valid @RequestBody ReorderDtos.WriteRequest r,Authentication a){return service.create(r,a.getName());}
 @PutMapping("/reorder-rules/{id}") @PreAuthorize("hasAuthority('inventory:manage')")
 public ReorderDtos.View update(@PathVariable UUID id,@Valid @RequestBody ReorderDtos.WriteRequest r,Authentication a){return service.update(id,r,a.getName());}
 @PostMapping("/reorder-rules/{id}/deactivate") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('inventory:manage')")
 public void deactivate(@PathVariable UUID id,Authentication a){service.deactivate(id,a.getName());}
 @GetMapping("/replenishment-suggestions") @PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
 public List<ReorderDtos.Suggestion> suggestions(Authentication auth){return service.suggestions(auth.getName());}
}

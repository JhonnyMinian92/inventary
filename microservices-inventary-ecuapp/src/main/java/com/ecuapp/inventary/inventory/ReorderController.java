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
 @GetMapping("/reorder-rules") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public List<ReorderDtos.View> list(){return service.list();}
 @PostMapping("/reorder-rules") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ReorderDtos.View create(@Valid @RequestBody ReorderDtos.WriteRequest r,Authentication a){return service.create(r,a.getName());}
 @PutMapping("/reorder-rules/{id}") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public ReorderDtos.View update(@PathVariable UUID id,@Valid @RequestBody ReorderDtos.WriteRequest r,Authentication a){return service.update(id,r,a.getName());}
 @PostMapping("/reorder-rules/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
 public void deactivate(@PathVariable UUID id,Authentication a){service.deactivate(id,a.getName());}
 @GetMapping("/replenishment-suggestions") @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','AUDITOR')")
 public List<ReorderDtos.Suggestion> suggestions(){return service.suggestions();}
}

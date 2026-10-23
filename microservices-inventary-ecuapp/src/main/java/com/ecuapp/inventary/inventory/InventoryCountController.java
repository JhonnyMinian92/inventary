package com.ecuapp.inventary.inventory;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/counts")
@PreAuthorize("hasAnyAuthority('inventory:manage','audit:read')")
public class InventoryCountController {
    private final InventoryCountService service;

    public InventoryCountController(InventoryCountService service) {
        this.service = service;
    }

    @GetMapping
    public Page<InventoryCountDtos.View> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public InventoryCountDtos.View get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:manage')")
    public InventoryCountDtos.View create(@RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody InventoryCountDtos.CreateRequest request,
                                          Authentication authentication) {
        return service.create(key, request, authentication.getName());
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('inventory:manage')")
    public InventoryCountDtos.View post(@PathVariable UUID id, Authentication authentication) {
        return service.post(id, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('inventory:manage')")
    public InventoryCountDtos.View cancel(@PathVariable UUID id, Authentication authentication) {
        return service.cancel(id, authentication.getName());
    }
}

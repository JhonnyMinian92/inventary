package com.ecuapp.inventary.sales;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/outbox-events")
public class OutboxAdminController {
    private final OutboxAdminService service;

    public OutboxAdminController(OutboxAdminService service) {
        this.service = service;
    }

    @PostMapping("/{id}/redrive")
    @PreAuthorize("hasAuthority('outbox:manage')")
    public OutboxAdminService.View redrive(@PathVariable UUID id, Authentication authentication) {
        return service.redrive(id, authentication.getName());
    }
}

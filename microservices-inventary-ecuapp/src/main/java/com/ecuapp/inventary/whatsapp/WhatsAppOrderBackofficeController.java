package com.ecuapp.inventary.whatsapp;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Internal (JWT) endpoints for staff that follow up WhatsApp orders; n8n keeps using the integration routes. */
@RestController
@RequestMapping("/api/v1/whatsapp-orders")
public class WhatsAppOrderBackofficeController {
    private final WhatsAppOrderBackofficeService service;

    public WhatsAppOrderBackofficeController(WhatsAppOrderBackofficeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public Page<WhatsAppDtos.OrderView> list(@RequestParam(required = false) WhatsAppOrderStatus status,
                                             @RequestParam(required = false) String customerRef,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                             @PageableDefault(size = 20) Pageable pageable,Authentication authentication) {
        return service.list(status, customerRef, from, to, pageable,authentication.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:read')")
    public WhatsAppDtos.OrderView get(@PathVariable UUID id,Authentication authentication) {
        return service.get(id,authentication.getName());
    }

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasAuthority('sales:manage')")
    public WhatsAppDtos.OrderView confirmPayment(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody WhatsAppDtos.ConfirmPaymentRequest request,
                                                 Authentication authentication) {
        return service.confirmPayment(id, key, request, authentication.getName());
    }
}

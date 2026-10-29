package com.ecuapp.inventary.subscriptions;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Reads need sales:read, writes sales:manage; discounts on prepayment additionally need sales:discount. */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) { this.service = service; }

    @GetMapping("/options") @PreAuthorize("hasAuthority('sales:read') or hasAuthority('catalog:read')")
    public SubscriptionDtos.OptionsView options(Authentication auth) { return service.options(canDiscount(auth)); }

    /** Read-only calendar of charges for a plan; used while configuring a service or a subscription. */
    @PostMapping("/preview") @PreAuthorize("hasAuthority('sales:read') or hasAuthority('catalog:read')")
    public SubscriptionDtos.PreviewView preview(@Valid @RequestBody SubscriptionDtos.PreviewRequest request) { return service.preview(request); }

    @GetMapping @PreAuthorize("hasAuthority('sales:read')")
    public Page<SubscriptionDtos.View> list(@RequestParam(required = false) Subscription.Status status, @RequestParam(required = false) UUID customerId,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, customerId, pageable);
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('sales:read')")
    public SubscriptionDtos.View get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('sales:manage')")
    public SubscriptionDtos.View create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SubscriptionDtos.CreateRequest request, Authentication auth) {
        return service.create(key, request, auth.getName(), canDiscount(auth));
    }

    @GetMapping("/{id}/cancellation-preview") @PreAuthorize("hasAuthority('sales:read')")
    public SubscriptionDtos.CancellationPreview cancellationPreview(@PathVariable UUID id) { return service.cancellationPreview(id); }

    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('sales:manage')")
    public SubscriptionDtos.View cancel(@PathVariable UUID id, @Valid @RequestBody SubscriptionDtos.CancelRequest request, Authentication auth) {
        return service.cancel(id, request.reason(), auth.getName());
    }

    @PostMapping("/{id}/prepay") @PreAuthorize("hasAuthority('sales:manage')")
    public SubscriptionDtos.View prepay(@PathVariable UUID id, @Valid @RequestBody SubscriptionDtos.PrepayRequest request, Authentication auth) {
        return service.prepay(id, request.discountPercent(), auth.getName(), canDiscount(auth));
    }

    private static boolean canDiscount(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "sales:discount".equals(a.getAuthority()));
    }
}

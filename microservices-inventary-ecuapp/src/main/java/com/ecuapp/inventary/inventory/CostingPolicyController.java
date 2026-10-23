package com.ecuapp.inventary.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Valuation policy. Reading requires financial reports permission; changing it requires costing:manage. */
@RestController
@RequestMapping("/api/v1/costing/policy")
public class CostingPolicyController {
    public record PolicyView(CostingMethod method, Instant effectiveFrom, String createdBy, String updatedBy, Instant updatedAt) {
        static PolicyView from(InventoryCostingConfig c) {
            return new PolicyView(c.getMethod(), c.getEffectiveFrom(), c.getCreatedBy(), c.getUpdatedBy(), c.getUpdatedAt());
        }
    }
    public record PolicyRequest(@NotNull CostingMethod method) {}

    private final InventoryCostingService costing;

    public CostingPolicyController(InventoryCostingService costing) {
        this.costing = costing;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('reports:financial','costing:manage')")
    public PolicyView get() {
        return PolicyView.from(costing.policy());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('costing:manage')")
    public PolicyView update(@Valid @RequestBody PolicyRequest request, Authentication authentication) {
        return PolicyView.from(costing.changePolicy(request.method(), authentication.getName()));
    }
}

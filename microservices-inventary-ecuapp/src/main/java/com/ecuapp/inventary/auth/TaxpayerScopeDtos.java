package com.ecuapp.inventary.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TaxpayerScopeDtos {
    private TaxpayerScopeDtos() {}
    public record GrantRequest(@NotNull UUID taxpayerId,@NotBlank @Size(min=3,max=500) String reason,
                               Instant validFrom,Instant validUntil) {}
    public record View(UUID id,UUID taxpayerId,String taxId,String legalName,String reason,String grantedBy,
                       Instant validFrom,Instant validUntil,boolean active,boolean effective,Instant revokedAt,String revokedBy) {
        static View from(UserTaxpayerScope scope,Instant now) {
            return new View(scope.getId(),scope.getTaxpayer().getId(),scope.getTaxpayer().getTaxId(),
                scope.getTaxpayer().getLegalName(),scope.getReason(),scope.getGrantedBy(),scope.getValidFrom(),
                scope.getValidUntil(),scope.isActive(),scope.isEffectiveAt(now),scope.getRevokedAt(),scope.getRevokedBy());
        }
    }
}

package com.ecuapp.inventary.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class WarehouseScopeDtos {
    private WarehouseScopeDtos() {}
    public record GrantRequest(@NotNull UUID warehouseId,@NotBlank @Size(min=3,max=500) String reason,Instant validFrom,Instant validUntil) {}
    public record View(UUID id,UUID warehouseId,String warehouseCode,String warehouseName,String reason,String grantedBy,
                       Instant validFrom,Instant validUntil,boolean active,boolean effective,Instant revokedAt,String revokedBy) {
        static View from(UserWarehouseScope s,Instant now) {
            return new View(s.getId(),s.getWarehouse().getId(),s.getWarehouse().getCode(),s.getWarehouse().getName(),s.getReason(),
                s.getGrantedBy(),s.getValidFrom(),s.getValidUntil(),s.isActive(),s.isEffectiveAt(now),s.getRevokedAt(),s.getRevokedBy());
        }
    }
}
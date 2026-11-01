package com.ecuapp.inventary.taxpayer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaxpayerDtos {
    private TaxpayerDtos() {}
    public record EstablishmentImport(@NotBlank @Pattern(regexp="[0-9]{3}") String code,
        @Size(max=180) String tradeName,@Size(max=500) String address,@Size(max=180) String location,
        @NotBlank @Size(max=80) String status,Boolean headOffice) {}
    public record ImportRequest(@NotBlank @Pattern(regexp="[0-9]{13}") String taxId,
        @NotBlank @Size(max=180) String legalName,@NotBlank @Size(max=100) String status,
        @Size(max=100) String taxpayerType,@Size(max=100) String taxRegime,
        @Size(max=40) String accountingRequired,@Size(max=40) String withholdingAgent,
        @Size(max=40) String specialTaxpayer,@NotBlank @Size(max=160) String sourceReference,
        @NotNull @Size(min=1,max=500) List<@Valid EstablishmentImport> establishments) {}
    public record ProfileView(UUID id,String taxId,String legalName,String statusRaw,String statusNormalized,
        String taxpayerType,String taxRegime,String accountingRequiredRaw,String withholdingAgentRaw,
        String specialTaxpayerRaw,String sourceReference,String freshnessStatus,Instant observedAt,String payloadHash) {
        static ProfileView from(TaxpayerProfile p){return new ProfileView(p.getId(),p.getTaxId(),p.getLegalName(),p.getStatusRaw(),
            p.getStatusNormalized(),p.getTaxpayerType(),p.getTaxRegime(),p.getAccountingRequiredRaw(),p.getWithholdingAgentRaw(),
            p.getSpecialTaxpayerRaw(),p.getSourceReference(),p.getFreshnessStatus(),p.getObservedAt(),p.getPayloadHash());}
    }
    public record EstablishmentView(UUID id,UUID taxpayerId,String code,String tradeName,String address,String location,
        String statusRaw,String statusNormalized,Boolean headOffice,Instant observedAt) {
        static EstablishmentView from(SriEstablishment e){return new EstablishmentView(e.getId(),e.getTaxpayer().getId(),e.getCode(),
            e.getTradeName(),e.getAddress(),e.getLocation(),e.getStatusRaw(),e.getStatusNormalized(),e.getHeadOffice(),e.getObservedAt());}
    }
    public record SnapshotView(UUID id,String sourceType,String sourceReference,String payloadHash,Map<String,Object> snapshot,
        Instant observedAt,String actor) {
        static SnapshotView from(TaxpayerSnapshot s){return new SnapshotView(s.getId(),s.getSourceType(),s.getSourceReference(),
            s.getPayloadHash(),s.getSnapshot(),s.getObservedAt(),s.getActor());}
    }
    public record AssignmentRequest(@NotNull UUID establishmentId,@NotNull WarehouseEstablishmentAssignmentType relationshipType,
        @NotNull Instant validFrom,@Min(0) int priority,@Size(max=80) String responsible) {}
    public record CloseAssignmentRequest(@NotNull Instant validTo) {}
    public record AssignmentView(UUID id,UUID warehouseId,UUID establishmentId,String taxpayerId,String establishmentCode,
        WarehouseEstablishmentAssignmentType relationshipType,Instant validFrom,Instant validTo,int priority,String responsible) {
        static AssignmentView from(WarehouseEstablishmentAssignment a){return new AssignmentView(a.getId(),a.getWarehouse().getId(),
            a.getEstablishment().getId(),a.getEstablishment().getTaxpayer().getTaxId(),a.getEstablishment().getCode(),
            a.getRelationshipType(),a.getValidFrom(),a.getValidTo(),a.getPriority(),a.getResponsible());}
    }
}

package com.ecuapp.inventary.taxpayer;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="taxpayer_profile", uniqueConstraints=@UniqueConstraint(name="uk_taxpayer_profile_tax_id", columnNames="tax_id"))
public class TaxpayerProfile {
    @Id private UUID id;
    @Column(name="tax_id", nullable=false, length=13) private String taxId;
    @Column(name="legal_name", nullable=false, length=180) private String legalName;
    @Column(name="status_raw", nullable=false, length=100) private String statusRaw;
    @Column(name="status_normalized", nullable=false, length=24) private String statusNormalized;
    @Column(name="taxpayer_type", length=100) private String taxpayerType;
    @Column(name="tax_regime", length=100) private String taxRegime;
    @Column(name="accounting_required_raw", length=40) private String accountingRequiredRaw;
    @Column(name="withholding_agent_raw", length=40) private String withholdingAgentRaw;
    @Column(name="special_taxpayer_raw", length=40) private String specialTaxpayerRaw;
    @Column(name="source_reference", nullable=false, length=160) private String sourceReference;
    @Column(name="freshness_status", nullable=false, length=24) private String freshnessStatus="MANUAL_REVIEW";
    @Column(name="observed_at", nullable=false) private Instant observedAt;
    @Column(name="payload_hash", nullable=false, length=64) private String payloadHash;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected TaxpayerProfile() {}
    public TaxpayerProfile(String taxId, String legalName, String statusRaw, String statusNormalized,
            String taxpayerType, String taxRegime, String accountingRequiredRaw, String withholdingAgentRaw,
            String specialTaxpayerRaw, String sourceReference, Instant observedAt, String payloadHash) {
        this.id=UUID.randomUUID(); this.taxId=taxId; apply(legalName,statusRaw,statusNormalized,taxpayerType,taxRegime,
            accountingRequiredRaw,withholdingAgentRaw,specialTaxpayerRaw,sourceReference,observedAt,payloadHash);
        this.createdAt=observedAt; this.updatedAt=observedAt;
    }
    public void apply(String legalName,String statusRaw,String statusNormalized,String taxpayerType,String taxRegime,
            String accountingRequiredRaw,String withholdingAgentRaw,String specialTaxpayerRaw,String sourceReference,
            Instant observedAt,String payloadHash) {
        this.legalName=legalName; this.statusRaw=statusRaw; this.statusNormalized=statusNormalized;
        this.taxpayerType=taxpayerType; this.taxRegime=taxRegime; this.accountingRequiredRaw=accountingRequiredRaw;
        this.withholdingAgentRaw=withholdingAgentRaw; this.specialTaxpayerRaw=specialTaxpayerRaw;
        this.sourceReference=sourceReference; this.observedAt=observedAt; this.payloadHash=payloadHash;
        this.freshnessStatus="MANUAL_REVIEW"; this.updatedAt=observedAt;
    }
    public UUID getId(){return id;} public String getTaxId(){return taxId;} public String getLegalName(){return legalName;}
    public String getStatusRaw(){return statusRaw;} public String getStatusNormalized(){return statusNormalized;}
    public String getTaxpayerType(){return taxpayerType;} public String getTaxRegime(){return taxRegime;}
    public String getAccountingRequiredRaw(){return accountingRequiredRaw;} public String getWithholdingAgentRaw(){return withholdingAgentRaw;}
    public String getSpecialTaxpayerRaw(){return specialTaxpayerRaw;} public String getSourceReference(){return sourceReference;}
    public String getFreshnessStatus(){return freshnessStatus;} public Instant getObservedAt(){return observedAt;}
    public String getPayloadHash(){return payloadHash;}
}

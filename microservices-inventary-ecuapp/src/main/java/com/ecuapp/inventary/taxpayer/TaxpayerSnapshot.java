package com.ecuapp.inventary.taxpayer;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="taxpayer_snapshot")
public class TaxpayerSnapshot {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="taxpayer_id", nullable=false) private TaxpayerProfile taxpayer;
    @Column(name="source_type", nullable=false, length=24) private String sourceType="MANUAL_IMPORT";
    @Column(name="source_reference", nullable=false, length=160) private String sourceReference;
    @Column(name="payload_hash", nullable=false, length=64) private String payloadHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb") private Map<String,Object> snapshot;
    @Column(name="observed_at", nullable=false) private Instant observedAt;
    @Column(nullable=false, length=80) private String actor;
    protected TaxpayerSnapshot() {}
    public TaxpayerSnapshot(TaxpayerProfile taxpayer,String sourceReference,String payloadHash,Map<String,Object> snapshot,Instant observedAt,String actor) {
        this.id=UUID.randomUUID(); this.taxpayer=taxpayer; this.sourceReference=sourceReference; this.payloadHash=payloadHash;
        this.snapshot=Map.copyOf(snapshot); this.observedAt=observedAt; this.actor=actor;
    }
    public UUID getId(){return id;} public String getSourceType(){return sourceType;} public String getSourceReference(){return sourceReference;}
    public String getPayloadHash(){return payloadHash;} public Map<String,Object> getSnapshot(){return snapshot;}
    public Instant getObservedAt(){return observedAt;} public String getActor(){return actor;}
}

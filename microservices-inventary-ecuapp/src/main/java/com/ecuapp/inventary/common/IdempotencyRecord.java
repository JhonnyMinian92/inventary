package com.ecuapp.inventary.common;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="idempotency_record",uniqueConstraints=@UniqueConstraint(name="uk_idempotency_scope_key",columnNames={"scope","idempotency_key"}))
public class IdempotencyRecord {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=120) private String scope;
 @Column(name="idempotency_key",nullable=false,length=120) private String idempotencyKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private RecordStatus status;
 @Column(name="resource_type",length=80) private String resourceType;
 @Column(name="resource_id") private UUID resourceId;
 @Column(name="response_status") private Short responseStatus;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_body",columnDefinition="jsonb") private Map<String,Object> responseBody;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="completed_at") private Instant completedAt;
 protected IdempotencyRecord(){}
 @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
 public UUID getId(){return id;} public String getScope(){return scope;} public String getIdempotencyKey(){return idempotencyKey;} public String getRequestHash(){return requestHash;} public RecordStatus getStatus(){return status;} public UUID getResourceId(){return resourceId;} public Instant getCreatedAt(){return createdAt;} public Instant getCompletedAt(){return completedAt;}
 public enum RecordStatus { IN_PROGRESS, COMPLETED, FAILED }
}

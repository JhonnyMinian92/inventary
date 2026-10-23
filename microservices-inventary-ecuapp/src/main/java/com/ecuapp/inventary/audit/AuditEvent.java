package com.ecuapp.inventary.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name="audit_event")
public class AuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=80) private String actor;
 @Column(nullable=false,length=120) private String action;
 @Column(name="resource_type",nullable=false,length=80) private String resourceType;
 @Column(name="resource_id") private UUID resourceId;
 @Column(nullable=false,length=20) private String outcome;
 @Column(name="correlation_id",length=80) private String correlationId;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> metadata;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 protected AuditEvent(){}
 public AuditEvent(String actor,String action,String resourceType,UUID resourceId,String outcome,String correlationId,Map<String,Object> metadata){
  this.actor=actor;this.action=action;this.resourceType=resourceType;this.resourceId=resourceId;this.outcome=outcome;this.correlationId=correlationId;
  this.metadata=metadata==null?Map.of():Map.copyOf(metadata);this.occurredAt=Instant.now();
 }
 public UUID getId(){return id;} public String getActor(){return actor;} public String getAction(){return action;}
 public String getResourceType(){return resourceType;} public UUID getResourceId(){return resourceId;} public String getOutcome(){return outcome;}
 public String getCorrelationId(){return correlationId;} public Map<String,Object> getMetadata(){return metadata;} public Instant getOccurredAt(){return occurredAt;}
}

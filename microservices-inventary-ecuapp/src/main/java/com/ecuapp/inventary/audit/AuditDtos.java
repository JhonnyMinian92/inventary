package com.ecuapp.inventary.audit;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public final class AuditDtos {
 private AuditDtos(){}
 public record View(UUID id,String actor,String action,String resourceType,UUID resourceId,String outcome,String correlationId,Map<String,Object> metadata,Instant occurredAt){
  static View from(AuditEvent e){return new View(e.getId(),e.getActor(),e.getAction(),e.getResourceType(),e.getResourceId(),e.getOutcome(),e.getCorrelationId(),e.getMetadata(),e.getOccurredAt());}
 }
}

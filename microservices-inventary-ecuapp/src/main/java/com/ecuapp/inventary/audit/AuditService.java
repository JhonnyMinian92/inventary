package com.ecuapp.inventary.audit;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
@Service
public class AuditService {
 private final AuditEventRepository repository;
 public AuditService(AuditEventRepository repository){this.repository=repository;}
 @Transactional
 public void record(String actor,String action,String resourceType,UUID resourceId,Map<String,Object> metadata){
  recordOutcome(actor,action,resourceType,resourceId,"SUCCESS",MDC.get("requestId"),metadata);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void recordOutcome(String actor,String action,String resourceType,UUID resourceId,String outcome,String correlationId,Map<String,Object> metadata){
  String safeActor=actor==null||actor.isBlank()?"system":actor;
  String safeOutcome=switch(outcome==null?"":outcome){case "SUCCESS","DENIED","FAILURE"->outcome;default->"FAILURE";};
  repository.save(new AuditEvent(safeActor,action,resourceType,resourceId,safeOutcome,correlationId,metadata));
 }
}

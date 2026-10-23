package com.ecuapp.inventary.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
 private final AuditEventRepository repository;
 public AuditService(AuditEventRepository repository){this.repository=repository;}
 @Transactional
 public void record(String actor,String action,String resourceType,UUID resourceId,Map<String,Object> metadata){
  repository.save(new AuditEvent(actor==null||actor.isBlank()?"system":actor,action,resourceType,resourceId,"SUCCESS",null,metadata));
 }
}

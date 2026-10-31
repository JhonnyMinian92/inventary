package com.ecuapp.inventary.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit writer.
 *
 * Business-operation events join the caller's transaction so evidence and state commit
 * together. HTTP failures are recorded in an independent transaction because the request
 * transaction may already have rolled back.
 */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc;
        this.mapper=mapper;
    }

    @Transactional
    public void record(String actor,String action,String resourceType,UUID resourceId,Map<String,Object> metadata) {
        insert(actor,action,resourceType,resourceId,"SUCCESS",MDC.get("requestId"),metadata);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordOutcome(String actor,String action,String resourceType,UUID resourceId,String outcome,
                              String correlationId,Map<String,Object> metadata) {
        insert(actor,action,resourceType,resourceId,outcome,correlationId,metadata);
    }

    private void insert(String actor,String action,String resourceType,UUID resourceId,String outcome,
                        String correlationId,Map<String,Object> metadata) {
        String safeActor=actor==null||actor.isBlank()?"system":actor.trim();
        String safeOutcome=switch(outcome==null?"":outcome) {
            case "SUCCESS","DENIED","FAILURE" -> outcome;
            default -> "FAILURE";
        };
        String json;
        try {
            json=mapper.writeValueAsString(metadata==null?Map.of():metadata);
        } catch(JsonProcessingException invalidMetadata) {
            throw new IllegalArgumentException("Audit metadata must be JSON serializable.",invalidMetadata);
        }
        jdbc.update("""
            INSERT INTO audit_event(id,actor,action,resource_type,resource_id,outcome,correlation_id,metadata,occurred_at)
            VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),?)
            """,
            UUID.randomUUID(),safeActor,action,resourceType,resourceId,safeOutcome,correlationId,json,
            Timestamp.from(Instant.now()));
    }
}
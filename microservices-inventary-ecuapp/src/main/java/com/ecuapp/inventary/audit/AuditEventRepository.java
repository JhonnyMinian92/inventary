package com.ecuapp.inventary.audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;
public interface AuditEventRepository extends JpaRepository<AuditEvent,UUID>{
 @Query("""
 select e from AuditEvent e
 where (:actor is null or e.actor=:actor)
 and (:action is null or e.action=:action)
 and (:resourceType is null or e.resourceType=:resourceType)
 and (:outcome is null or e.outcome=:outcome)
 and e.occurredAt>=:fromTime
 and e.occurredAt<:toTime
  """)
 Page<AuditEvent> search(@Param("actor") String actor,@Param("action") String action,
 @Param("resourceType") String resourceType,@Param("outcome") String outcome,
 @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,Pageable pageable);
}

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

 @Query(value="""
 select e.* from audit_event e
 where (:actor is null or e.actor=:actor)
 and (:action is null or e.action=:action)
 and (:resourceType is null or e.resource_type=:resourceType)
 and (:outcome is null or e.outcome=:outcome)
 and e.occurred_at>=:fromTime and e.occurred_at<:toTime
 and (
   e.metadata ->> 'warehouseId' in (:warehouseIds)
   or e.metadata ->> 'sourceWarehouseId' in (:warehouseIds)
   or e.metadata ->> 'destinationWarehouseId' in (:warehouseIds)
   or (e.resource_type='HTTP_REQUEST' and e.outcome='DENIED')
 )
 order by e.occurred_at desc
 """,countQuery="""
 select count(*) from audit_event e
 where (:actor is null or e.actor=:actor)
 and (:action is null or e.action=:action)
 and (:resourceType is null or e.resource_type=:resourceType)
 and (:outcome is null or e.outcome=:outcome)
 and e.occurred_at>=:fromTime and e.occurred_at<:toTime
 and (
   e.metadata ->> 'warehouseId' in (:warehouseIds)
   or e.metadata ->> 'sourceWarehouseId' in (:warehouseIds)
   or e.metadata ->> 'destinationWarehouseId' in (:warehouseIds)
   or (e.resource_type='HTTP_REQUEST' and e.outcome='DENIED')
 )""",nativeQuery=true)
 Page<AuditEvent> searchScoped(@Param("actor") String actor,@Param("action") String action,
  @Param("resourceType") String resourceType,@Param("outcome") String outcome,@Param("fromTime") Instant fromTime,
  @Param("toTime") Instant toTime,@Param("warehouseIds") java.util.Collection<String> warehouseIds,Pageable pageable);
}

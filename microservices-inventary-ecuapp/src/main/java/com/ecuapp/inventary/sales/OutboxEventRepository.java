package com.ecuapp.inventary.sales;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID>{
 long countByStatus(String status);
 @Query("select min(e.createdAt) from OutboxEvent e where e.status in ('PENDING','PROCESSING')")
 Instant oldestUnpublished();
 @Query("""
  select e from OutboxEvent e
  where (:status is null or e.status=:status)
  and (:aggregateType is null or e.aggregateType=:aggregateType)
  and (:aggregateId is null or e.aggregateId=:aggregateId)
  """)
 Page<OutboxEvent> search(@Param("status") String status,@Param("aggregateType") String aggregateType,@Param("aggregateId") UUID aggregateId,Pageable pageable);
}

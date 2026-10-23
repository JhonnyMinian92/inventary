package com.ecuapp.inventary.sales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 List<OutboxEvent> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String status,Instant instant);
}

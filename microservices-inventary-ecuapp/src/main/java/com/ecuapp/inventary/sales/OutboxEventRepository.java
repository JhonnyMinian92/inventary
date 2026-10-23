package com.ecuapp.inventary.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID> {
    /**
     * Claimable events are selected and locked in the same short transaction as status change.
     * SKIP LOCKED lets multiple publisher instances work on disjoint batches.
     */
    @Query(value = """
        SELECT *
        FROM outbox_event
        WHERE (status = 'PENDING' OR status = 'PROCESSING')
          AND next_attempt_at <= :now
        ORDER BY created_at ASC, id ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockReadyForClaim(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxEvent> findByIdAndStatusAndNextAttemptAt(UUID id, String status, Instant nextAttemptAt);
}

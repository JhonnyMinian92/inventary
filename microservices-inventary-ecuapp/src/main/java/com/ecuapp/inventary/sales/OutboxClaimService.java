package com.ecuapp.inventary.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Keeps database transactions short: claim rows under FOR UPDATE SKIP LOCKED,
 * commit the PROCESSING lease, then let the publisher perform network I/O elsewhere.
 */
@Service
public class OutboxClaimService {
    private static final int MAX_BATCH_SIZE = 50;
    private static final Duration LEASE = Duration.ofMinutes(10);

    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<OutboxEvent> claimPending() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant leaseUntil = now.plus(LEASE).truncatedTo(ChronoUnit.MICROS);
        List<OutboxEvent> events = repository.lockReadyForClaim(now, MAX_BATCH_SIZE);
        if (events.isEmpty()) return List.of();
        events.forEach(event -> event.claim(leaseUntil));
        repository.saveAllAndFlush(events);
        return List.copyOf(events);
    }

    @Transactional
    public void markPublished(UUID id, Instant leaseUntil) {
        repository.findByIdAndStatusAndNextAttemptAt(id, "PROCESSING", leaseUntil).ifPresent(event -> {
            event.markPublished();
            repository.saveAndFlush(event);
        });
    }

    @Transactional
    public void markFailed(UUID id, Instant leaseUntil) {
        repository.findByIdAndStatusAndNextAttemptAt(id, "PROCESSING", leaseUntil).ifPresent(event -> {
            event.markFailed();
            repository.saveAndFlush(event);
        });
    }
}

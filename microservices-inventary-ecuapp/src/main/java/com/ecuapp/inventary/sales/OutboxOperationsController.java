package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Diagnostics and safe redrive of outbox events (permission operations:manage). Payloads are never modified. */
@RestController
@RequestMapping("/api/v1/operations/outbox")
@PreAuthorize("hasAuthority('operations:manage')")
public class OutboxOperationsController {
    public record EventView(UUID id, String aggregateType, UUID aggregateId, String eventType, String status, int attempts,
                            Instant nextAttemptAt, Instant createdAt, Instant publishedAt, Instant lockedUntil, String lastError) {
        static EventView from(OutboxEvent e) {
            return new EventView(e.getId(), e.getAggregateType(), e.getAggregateId(), e.getEventType(), e.getStatus(), e.getAttempts(),
                e.getNextAttemptAt(), e.getCreatedAt(), e.getPublishedAt(), e.getLockedUntil(), e.getLastError());
        }
    }
    public record Stats(long pending, long processing, long published, long failed, Long oldestUnpublishedAgeSeconds) {}

    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "PUBLISHED", "FAILED");
    private final OutboxEventRepository repository;
    private final AuditService audit;

    public OutboxOperationsController(OutboxEventRepository repository, AuditService audit, MeterRegistry meters) {
        this.repository = repository;
        this.audit = audit;
        // Low-cardinality operational gauges for alerting on stuck or failed deliveries.
        Gauge.builder("inventary.outbox.events", repository, r -> r.countByStatus("PENDING")).tag("status", "pending").register(meters);
        Gauge.builder("inventary.outbox.events", repository, r -> r.countByStatus("FAILED")).tag("status", "failed").register(meters);
        Gauge.builder("inventary.outbox.oldest.unpublished.seconds", repository, r -> {
            Instant oldest = r.oldestUnpublished();
            return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toSeconds();
        }).register(meters);
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        Instant oldest = repository.oldestUnpublished();
        return new Stats(repository.countByStatus("PENDING"), repository.countByStatus("PROCESSING"), repository.countByStatus("PUBLISHED"),
            repository.countByStatus("FAILED"), oldest == null ? null : Duration.between(oldest, Instant.now()).toSeconds());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<EventView> list(@RequestParam(required = false) String status, @RequestParam(required = false) String aggregateType,
                                @RequestParam(required = false) UUID aggregateId, @PageableDefault(size = 20) Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !STATUSES.contains(normalized)) throw new InvalidQueryException("Estado de Outbox inválido.");
        String aggregate = aggregateType == null || aggregateType.isBlank() ? null : aggregateType.trim().toUpperCase(Locale.ROOT);
        return repository.search(normalized, aggregate, aggregateId,
            Pages.of(pageable, Set.of("createdAt", "status", "attempts"), Sort.by(Sort.Direction.ASC, "createdAt"))).map(EventView::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public EventView get(@PathVariable UUID id) {
        return EventView.from(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento Outbox", id)));
    }

    /** Requeues a FAILED (or stuck) event for immediate delivery; published events cannot be replayed from here. */
    @PostMapping("/{id}/retry")
    @Transactional
    public EventView retry(@PathVariable UUID id, Authentication authentication) {
        OutboxEvent event = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento Outbox", id));
        String previous = event.getStatus();
        if (!event.requeue()) throw new ConflictException("El evento ya fue publicado; los consumidores deben reprocesarlo desde Kafka.");
        audit.record(authentication.getName(), "OUTBOX_EVENT_REQUEUED", "OUTBOX_EVENT", id,
            Map.of("previousStatus", previous, "eventType", event.getEventType()));
        return EventView.from(event);
    }
}

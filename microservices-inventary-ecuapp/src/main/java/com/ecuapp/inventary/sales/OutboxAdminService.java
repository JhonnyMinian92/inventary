package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxAdminService {
    public record View(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       String status, int attempts, Instant createdAt, Instant nextAttemptAt,
                       Instant publishedAt) {
        static View from(OutboxEvent event) {
            return new View(event.getId(), event.getAggregateType(), event.getAggregateId(),
                    event.getEventType(), event.getStatus(), event.getAttempts(),
                    event.getCreatedAt(), event.getNextAttemptAt(), event.getPublishedAt());
        }
    }

    private final OutboxEventRepository repository;
    private final AuditService audit;

    public OutboxAdminService(OutboxEventRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public View redrive(UUID id, String actor) {
        OutboxEvent event = repository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento Outbox", id));
        if (!"FAILED".equals(event.getStatus())) {
            throw new ConflictException("Solo se pueden reactivar eventos Outbox en estado FAILED.");
        }
        event.redrive();
        repository.saveAndFlush(event);
        audit.record(actor, "OUTBOX_EVENT_REDRIVEN", "OUTBOX_EVENT", id,
                Map.of("aggregateType", event.getAggregateType(), "eventType", event.getEventType()));
        return View.from(event);
    }
}

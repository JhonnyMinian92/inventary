package com.ecuapp.inventary.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relays outbox rows without holding database locks during network calls:
 * <ol>
 * <li>a short transaction claims a batch with {@code FOR UPDATE SKIP LOCKED} and a lease (PROCESSING + locked_until);</li>
 * <li>each event is sent outside any transaction;</li>
 * <li>a second short transaction records PUBLISHED or schedules a retry.</li>
 * </ol>
 * Expired leases (process crash after claiming) are reclaimed by the next run, so delivery is at-least-once.
 */
@Component
public class OutboxRelay {
    /** Transport abstraction; the Kafka implementation is registered only when Kafka is enabled. */
    @FunctionalInterface
    public interface Sender {
        void send(String key, String json) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String CLAIM_SQL = """
        UPDATE outbox_event SET status = 'PROCESSING', locked_until = :lease
        WHERE id IN (
          SELECT id FROM outbox_event
          WHERE (status = 'PENDING' AND next_attempt_at <= :now) OR (status = 'PROCESSING' AND locked_until < :now)
          ORDER BY created_at ASC
          LIMIT :batch
          FOR UPDATE SKIP LOCKED)
        RETURNING id
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public OutboxRelay(NamedParameterJdbcTemplate jdbc, OutboxEventRepository repository, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return number of events published in this run */
    public int relay(Sender sender, int batchSize, Duration lease) {
        Instant now = Instant.now();
        List<UUID> claimed = tx.execute(status -> jdbc.queryForList(CLAIM_SQL, new MapSqlParameterSource()
            .addValue("lease", Timestamp.from(now.plus(lease)))
            .addValue("now", Timestamp.from(now))
            .addValue("batch", batchSize), UUID.class));
        int published = 0;
        for (UUID id : claimed == null ? List.<UUID>of() : claimed) {
            OutboxEvent event = repository.findById(id).orElse(null);
            if (event == null) continue;
            String error = null;
            try {
                sender.send(event.getId().toString(), envelope(event));
            } catch (Exception ex) {
                error = ex.getClass().getSimpleName();
                log.warn("No se pudo publicar evento Outbox id={} intento={} causa={}", id, event.getAttempts() + 1, error);
            }
            String failure = error;
            boolean ok = Boolean.TRUE.equals(tx.execute(status -> {
                OutboxEvent current = repository.findById(id).orElse(null);
                if (current == null || !"PROCESSING".equals(current.getStatus())) return false;
                if (failure == null) {
                    current.markPublished();
                    return true;
                }
                int attempts = jdbc.getJdbcTemplate().queryForObject(
                    "select attempts from outbox_event where id=?", Integer.class, id);
                int nextAttempts = attempts + 1;
                boolean terminal = nextAttempts >= OutboxEvent.MAX_ATTEMPTS;
                long delaySeconds = Math.min(3600L, 1L << Math.min(nextAttempts, 11));
                jdbc.getJdbcTemplate().update("""
                    update outbox_event
                    set attempts=?, status=?, locked_until=null, last_error=?,
                        next_attempt_at=now()+(? * interval '1 second')
                    where id=?""",
                    nextAttempts, terminal ? "FAILED" : "PENDING",
                    failure.substring(0, Math.min(failure.length(), 500)),
                    terminal ? 0L : delaySeconds, id);
                return failure == null;
            }));
            if (ok) published++;
        }
        return published;
    }

    String envelope(OutboxEvent event) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", event.getSchemaVersion());
        envelope.put("eventId", event.getId().toString());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("aggregateId", event.getAggregateId().toString());
        if (event.getAggregateVersion() != null) envelope.put("aggregateVersion", event.getAggregateVersion());
        envelope.put("eventType", event.getEventType());
        envelope.put("occurredAt", event.getCreatedAt().toString());
        if (event.getCorrelationId() != null) envelope.put("correlationId", event.getCorrelationId());
        if (event.getCausationId() != null) envelope.put("causationId", event.getCausationId());
        envelope.put("payload", event.getPayload());
        return mapper.writeValueAsString(envelope);
    }
}

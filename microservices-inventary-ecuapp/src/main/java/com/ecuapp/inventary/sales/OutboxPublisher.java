package com.ecuapp.inventary.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="app.kafka.enabled",havingValue="true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService claims;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public OutboxPublisher(OutboxClaimService claims, KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                           @Value("${app.kafka.topic:inventary.domain-events}") String topic) {
        this.claims = claims;
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    /**
     * Rows are claimed in a short DB transaction. Kafka I/O then happens without an open
     * database transaction; delivery is at-least-once and consumers must deduplicate eventId.
     */
    @Scheduled(fixedDelayString = "${app.kafka.poll-delay-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> events = claims.claimPending();
        for (OutboxEvent event : events) {
            Instant leaseUntil = event.getNextAttemptAt();
            try {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("eventId", event.getId().toString());
                envelope.put("aggregateType", event.getAggregateType());
                envelope.put("aggregateId", event.getAggregateId().toString());
                envelope.put("eventType", event.getEventType());
                envelope.put("occurredAt", event.getCreatedAt().toString());
                envelope.put("payload", event.getPayload());
                String json = mapper.writeValueAsString(envelope);
                kafka.send(topic, event.getId().toString(), json).get(5, TimeUnit.SECONDS);
            } catch (Exception publishFailure) {
                try {
                    claims.markFailed(event.getId(), leaseUntil);
                } catch (RuntimeException persistenceFailure) {
                    log.error("No se pudo guardar el fallo de Outbox id={}; el lease permitirá recuperar el evento.",
                            event.getId(), persistenceFailure);
                }
                log.warn("No se pudo publicar evento Outbox id={}, intento={}, errorType={}",
                        event.getId(), event.getAttempts(), publishFailure.getClass().getSimpleName());
                continue;
            }

            try {
                claims.markPublished(event.getId(), leaseUntil);
            } catch (RuntimeException persistenceFailure) {
                // Kafka accepted the event; after lease expiry it can be redelivered. Keep eventId stable.
                log.error("Kafka aceptó el evento Outbox id={} pero no se pudo confirmar su publicación; "
                        + "se podrá reenviar tras vencer el lease y el consumidor debe deduplicar eventId.",
                        event.getId(), persistenceFailure);
            }
        }
    }
}

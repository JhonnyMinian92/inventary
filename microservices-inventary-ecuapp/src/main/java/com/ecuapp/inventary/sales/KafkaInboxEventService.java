package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.common.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Idempotent Kafka event processor. The inbox row is committed before the listener acknowledges
 * the Kafka record; a redelivery with the same event ID and payload becomes a no-op.
 * At present this consumer's side effect is public-catalog cache invalidation.
 */
@Service
public class KafkaInboxEventService {
    public enum Result { PROCESSED, DUPLICATE }

    private final KafkaInboxEventRepository inbox;
    private final ObjectMapper mapper;
    private final CacheManager cacheManager;
    private final int maxMessageBytes;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter integrityConflictCounter;

    public KafkaInboxEventService(
        KafkaInboxEventRepository inbox,
        ObjectMapper mapper,
        CacheManager cacheManager,
        MeterRegistry meters,
        @Value("${app.kafka.consumer.max-message-bytes:1048576}") int maxMessageBytes
    ) {
        if (maxMessageBytes < 1) throw new IllegalStateException("El tamaño máximo de evento Kafka debe ser positivo.");
        this.inbox = inbox;
        this.mapper = mapper;
        this.cacheManager = cacheManager;
        this.maxMessageBytes = maxMessageBytes;
        this.processedCounter = Counter.builder("inventary.kafka.consumer.events").tag("outcome", "processed").register(meters);
        this.duplicateCounter = Counter.builder("inventary.kafka.consumer.events").tag("outcome", "duplicate").register(meters);
        this.integrityConflictCounter = Counter.builder("inventary.kafka.consumer.events").tag("outcome", "integrity_conflict").register(meters);
    }

    @Transactional
    public Result process(String rawEnvelope) {
        if (rawEnvelope == null || rawEnvelope.isBlank()) throw new IllegalArgumentException("El evento Kafka está vacío.");
        if (rawEnvelope.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new IllegalArgumentException("El evento Kafka supera el tamaño máximo permitido.");
        }

        final JsonNode envelope;
        try {
            envelope = mapper.readTree(rawEnvelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("El evento Kafka no contiene un envelope JSON válido.");
        }
        if (envelope == null || !envelope.isObject()) throw new IllegalArgumentException("El envelope Kafka debe ser un objeto JSON.");

        UUID eventId = uuid(envelope, "eventId");
        UUID aggregateId = uuid(envelope, "aggregateId");
        String eventType = requiredText(envelope, "eventType", 120);
        String aggregateType = requiredText(envelope, "aggregateType", 80);
        JsonNode payload = envelope.get("payload");
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("El payload Kafka debe ser un objeto JSON.");

        String hash = sha256(rawEnvelope);
        int inserted = inbox.insertIfAbsent(eventId, eventType, aggregateType, aggregateId, hash);
        if (inserted == 0) {
            String previousHash = inbox.findPayloadHash(eventId);
            if (hash.equals(previousHash)) {
                duplicateCounter.increment();
                return Result.DUPLICATE;
            }
            integrityConflictCounter.increment();
            throw new ConflictException("Se recibió un evento con el mismo eventId pero con un contenido diferente.");
        }

        // Event delivery can be at-least-once. Clearing is safe to repeat and ensures that stock
        // or catalog changes emitted by another application instance don't leave stale public results.
        Cache publicProducts = cacheManager.getCache("publicProductList");
        if (publicProducts != null) publicProducts.clear();
        processedCounter.increment();
        return Result.PROCESSED;
    }

    private UUID uuid(JsonNode envelope, String field) {
        String value = requiredText(envelope, field, 36);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("El campo " + field + " debe contener un UUID válido.");
        }
    }

    private String requiredText(JsonNode envelope, String field, int maxLength) {
        JsonNode value = envelope.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Falta el campo obligatorio " + field + ".");
        String text = value.asText().trim();
        if (text.isEmpty() || text.length() > maxLength) throw new IllegalArgumentException("El campo " + field + " no es válido.");
        return text;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 no está disponible en este runtime.", ex);
        }
    }
}

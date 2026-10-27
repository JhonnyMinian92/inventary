package com.ecuapp.inventary.sales;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Uses PostgreSQL's unique event_id constraint as the concurrency-safe idempotency boundary. */
@Repository
public class KafkaInboxEventRepository {
    private final JdbcTemplate jdbc;

    public KafkaInboxEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns 1 for the first delivery and 0 for a duplicate event ID. */
    public int insertIfAbsent(UUID eventId, String eventType, String aggregateType, UUID aggregateId, String payloadHash) {
        return jdbc.update("""
            INSERT INTO kafka_inbox_event(event_id, event_type, aggregate_type, aggregate_id, payload_sha256)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """, eventId, eventType, aggregateType, aggregateId, payloadHash);
    }

    public String findPayloadHash(UUID eventId) {
        List<String> hashes = jdbc.query(
            "SELECT payload_sha256 FROM kafka_inbox_event WHERE event_id = ?",
            (rs, rowNum) -> rs.getString(1), eventId);
        return hashes.isEmpty() ? null : hashes.get(0).trim();
    }
}

-- Deduplicates at-least-once Kafka delivery by the immutable Outbox event ID.
-- Store only event metadata and a digest; never persist tokens, message bodies, or customer secrets.
CREATE TABLE kafka_inbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_kafka_inbox_payload_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_kafka_inbox_received_at ON kafka_inbox_event (received_at);
CREATE INDEX idx_kafka_inbox_aggregate ON kafka_inbox_event (aggregate_type, aggregate_id);

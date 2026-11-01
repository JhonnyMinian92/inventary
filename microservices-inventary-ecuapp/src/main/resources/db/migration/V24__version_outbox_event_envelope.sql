-- Version the event contract without rewriting historical events or losing pending deliveries.
ALTER TABLE outbox_event
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    ADD COLUMN correlation_id VARCHAR(80),
    ADD COLUMN causation_id VARCHAR(80),
    ADD COLUMN aggregate_version BIGINT CHECK (aggregate_version IS NULL OR aggregate_version >= 0);

CREATE INDEX ix_outbox_correlation_created
    ON outbox_event(correlation_id, created_at)
    WHERE correlation_id IS NOT NULL;

COMMENT ON COLUMN outbox_event.schema_version IS 'Version of the serialized domain-event envelope contract.';
COMMENT ON COLUMN outbox_event.correlation_id IS 'Request/workflow correlation propagated across asynchronous boundaries.';
COMMENT ON COLUMN outbox_event.causation_id IS 'Identifier of the event or command that directly caused this event.';
COMMENT ON COLUMN outbox_event.aggregate_version IS 'Monotonic aggregate version when the producer can provide it; NULL otherwise.';

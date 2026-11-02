CREATE TABLE integration_operation (
    id UUID PRIMARY KEY,
    partner_code VARCHAR(80),
    operation_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(100) NOT NULL DEFAULT 'inventary-api',
    source_operation_id VARCHAR(160),
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    correlation_id VARCHAR(160),
    schema_version VARCHAR(32) NOT NULL DEFAULT '1',
    attempts INTEGER NOT NULL DEFAULT 0,
    safe_error_code VARCHAR(80),
    safe_error_message VARCHAR(500),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160),
    CONSTRAINT uk_integration_operation_idempotency UNIQUE(idempotency_key,request_hash)
);

CREATE TABLE webhook_subscription (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    endpoint TEXT NOT NULL,
    allowed_events VARCHAR(2000) NOT NULL,
    secret_ref VARCHAR(160),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(160)
);

CREATE TABLE webhook_delivery (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES webhook_subscription(id) ON DELETE CASCADE,
    event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_status INTEGER,
    response_body_hash VARCHAR(64),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_integration_operation_status_next ON integration_operation(status,next_attempt_at);
CREATE INDEX ix_integration_operation_source ON integration_operation(source_operation_id);
CREATE INDEX ix_webhook_delivery_status_next ON webhook_delivery(status,next_attempt_at);

INSERT INTO app_permission(id,code,description) VALUES
('30000000-0000-0000-0000-000000000001','integration:read','Consultar operaciones y entregas de integraciones'),
('30000000-0000-0000-0000-000000000002','integration:manage','Crear, reintentar y administrar integraciones')
ON CONFLICT (code) DO NOTHING;
INSERT INTO app_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code IN ('integration:read','integration:manage')
WHERE r.code IN ('SUPER_ADMIN','SOPORTE_TI')
ON CONFLICT DO NOTHING;

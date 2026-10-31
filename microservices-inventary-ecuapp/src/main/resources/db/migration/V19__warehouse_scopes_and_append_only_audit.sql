-- Fase 2: ámbitos explícitos y temporales por bodega; evidencia de auditoría inmutable.
CREATE TABLE user_warehouse_scope (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NOT NULL,
    granted_by VARCHAR(80) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_warehouse_scope_reason CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
    CONSTRAINT ck_user_warehouse_scope_validity CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_user_warehouse_scope_revocation CHECK (
      (active AND revoked_at IS NULL AND revoked_by IS NULL)
      OR (NOT active AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_user_warehouse_scope_active ON user_warehouse_scope(user_id,warehouse_id) WHERE active=true;
CREATE INDEX ix_user_warehouse_scope_effective ON user_warehouse_scope(user_id,valid_from,valid_until) WHERE active=true;
CREATE INDEX ix_user_warehouse_scope_warehouse ON user_warehouse_scope(warehouse_id,user_id) WHERE active=true;

CREATE OR REPLACE FUNCTION prevent_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only; record a compensating audit event instead.';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_event_immutable BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();
CREATE TRIGGER trg_audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_event_mutation();
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_metadata_object CHECK (jsonb_typeof(metadata)='object');

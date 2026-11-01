-- Phase 2/5: explicit, time-bounded per-user access to taxpayer (RUC) records.
CREATE TABLE user_taxpayer_scope (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    taxpayer_id UUID NOT NULL REFERENCES taxpayer_profile(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NOT NULL,
    granted_by VARCHAR(80) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_taxpayer_scope_period CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE UNIQUE INDEX uk_user_taxpayer_scope_active
    ON user_taxpayer_scope(user_id,taxpayer_id) WHERE active=TRUE;
CREATE INDEX ix_user_taxpayer_scope_effective
    ON user_taxpayer_scope(user_id,valid_from,valid_until) WHERE active=TRUE;
CREATE INDEX ix_user_taxpayer_scope_taxpayer
    ON user_taxpayer_scope(taxpayer_id,user_id) WHERE active=TRUE;

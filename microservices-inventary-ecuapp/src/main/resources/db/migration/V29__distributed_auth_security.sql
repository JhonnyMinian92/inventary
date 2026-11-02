-- Shared authentication state across every application instance.
CREATE TABLE auth_login_attempt (
    subject_hash CHAR(64) PRIMARY KEY,
    failures INTEGER NOT NULL CHECK (failures >= 0),
    window_started_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_auth_login_attempt_updated ON auth_login_attempt(updated_at);
ALTER TABLE app_user ADD COLUMN email VARCHAR(254);
ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_email_format CHECK (email IS NULL OR email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$');
CREATE UNIQUE INDEX uk_app_user_email_ci ON app_user(lower(email)) WHERE email IS NOT NULL;
CREATE TABLE auth_password_reset (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_auth_password_reset_user ON auth_password_reset(user_id, expires_at DESC);
CREATE TABLE auth_mfa (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    secret_ciphertext TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMPTZ
);
CREATE TABLE auth_security_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_auth_security_event_time ON auth_security_event(occurred_at DESC);

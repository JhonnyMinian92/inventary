-- Phase 5: preserve tax registry evidence separately from operational warehouses.
CREATE TABLE taxpayer_profile (
    id UUID PRIMARY KEY,
    tax_id VARCHAR(13) NOT NULL,
    legal_name VARCHAR(180) NOT NULL,
    status_raw VARCHAR(100) NOT NULL,
    status_normalized VARCHAR(24) NOT NULL,
    taxpayer_type VARCHAR(100),
    tax_regime VARCHAR(100),
    accounting_required_raw VARCHAR(40),
    withholding_agent_raw VARCHAR(40),
    special_taxpayer_raw VARCHAR(40),
    source_reference VARCHAR(160) NOT NULL,
    freshness_status VARCHAR(24) NOT NULL DEFAULT 'MANUAL_REVIEW',
    observed_at TIMESTAMPTZ NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_taxpayer_profile_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_taxpayer_profile_tax_id CHECK (tax_id ~ '^[0-9]{13}$'),
    CONSTRAINT ck_taxpayer_profile_freshness CHECK (freshness_status IN ('MANUAL_REVIEW','STALE','SOURCE_UNAVAILABLE','SOURCE_ERROR'))
);

CREATE TABLE sri_establishment (
    id UUID PRIMARY KEY,
    taxpayer_id UUID NOT NULL REFERENCES taxpayer_profile(id) ON DELETE RESTRICT,
    establishment_code VARCHAR(3) NOT NULL,
    trade_name VARCHAR(180),
    address VARCHAR(500),
    location VARCHAR(180),
    status_raw VARCHAR(80) NOT NULL,
    status_normalized VARCHAR(24) NOT NULL,
    head_office BOOLEAN,
    observed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_sri_establishment_profile_code UNIQUE (taxpayer_id, establishment_code),
    CONSTRAINT ck_sri_establishment_code CHECK (establishment_code ~ '^[0-9]{3}$')
);
CREATE INDEX ix_sri_establishment_taxpayer ON sri_establishment(taxpayer_id, establishment_code);

-- One snapshot stores the normalized fields received together, not credentials or an unrestricted provider payload.
CREATE TABLE taxpayer_snapshot (
    id UUID PRIMARY KEY,
    taxpayer_id UUID NOT NULL REFERENCES taxpayer_profile(id) ON DELETE RESTRICT,
    source_type VARCHAR(24) NOT NULL,
    source_reference VARCHAR(160) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    snapshot JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    actor VARCHAR(80) NOT NULL,
    CONSTRAINT ck_taxpayer_snapshot_source CHECK (source_type = 'MANUAL_IMPORT')
);
CREATE INDEX ix_taxpayer_snapshot_history ON taxpayer_snapshot(taxpayer_id, observed_at DESC);

CREATE TABLE warehouse_establishment_assignment (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
    establishment_id UUID NOT NULL REFERENCES sri_establishment(id) ON DELETE RESTRICT,
    relationship_type VARCHAR(32) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    priority INTEGER NOT NULL DEFAULT 0,
    responsible VARCHAR(80),
    created_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_warehouse_establishment_assignment_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_warehouse_establishment_assignment_type CHECK (relationship_type IN
      ('STOCK_SERVES','SALES_OPERATIONS','PICKUP','FULFILLMENT','RECEIVING','RETURN_ACCEPTANCE','REPORTING_SCOPE'))
);
CREATE INDEX ix_warehouse_establishment_active ON warehouse_establishment_assignment(warehouse_id, valid_to);
CREATE INDEX ix_establishment_warehouse_active ON warehouse_establishment_assignment(establishment_id, valid_to);
CREATE UNIQUE INDEX uk_warehouse_establishment_active_type
    ON warehouse_establishment_assignment(warehouse_id, establishment_id, relationship_type)
    WHERE valid_to IS NULL;

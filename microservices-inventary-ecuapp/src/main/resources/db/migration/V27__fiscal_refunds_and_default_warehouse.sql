CREATE TABLE tax_rate (
    id uuid PRIMARY KEY,
    code varchar(24) NOT NULL,
    name varchar(160) NOT NULL,
    percentage numeric(7,4) NOT NULL CHECK (percentage >= 0),
    valid_from date NOT NULL,
    valid_until date,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tax_rate_code_period UNIQUE (code, valid_from),
    CONSTRAINT chk_tax_rate_period CHECK (valid_until IS NULL OR valid_until >= valid_from)
);

ALTER TABLE product ADD COLUMN IF NOT EXISTS tax_code varchar(24) NOT NULL DEFAULT 'IVA_15';
ALTER TABLE product ADD COLUMN IF NOT EXISTS tax_treatment varchar(24) NOT NULL DEFAULT 'TAXABLE';
ALTER TABLE product ADD CONSTRAINT chk_product_tax_treatment CHECK (tax_treatment IN ('TAXABLE','ZERO_RATED','EXEMPT','NOT_SUBJECT'));

ALTER TABLE user_warehouse_scope ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_default_warehouse_per_user
    ON user_warehouse_scope(user_id) WHERE is_default AND active = true AND revoked_at IS NULL;

ALTER TABLE sale ADD COLUMN IF NOT EXISTS tax_rate_code varchar(24);
ALTER TABLE sale ADD COLUMN IF NOT EXISTS tax_rate_percentage numeric(7,4);
ALTER TABLE sale ADD COLUMN IF NOT EXISTS fiscal_snapshot jsonb;

INSERT INTO tax_rate(id, code, name, percentage, valid_from, valid_until, active)
VALUES
    (gen_random_uuid(), 'IVA_15', 'Tarifa IVA vigente', 15, DATE '2024-04-01', NULL, true),
    (gen_random_uuid(), 'IVA_0', 'Tarifa IVA 0%', 0, DATE '1900-01-01', NULL, true)
ON CONFLICT DO NOTHING;

CREATE TABLE payment_refund (
    id uuid PRIMARY KEY,
    refund_number varchar(80) NOT NULL UNIQUE,
    source_sale_id uuid NOT NULL REFERENCES sale(id),
    sales_return_id uuid REFERENCES sales_return(id),
    status varchar(24) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL,
    payment_provider varchar(80),
    provider_reference varchar(160),
    reason varchar(1000) NOT NULL,
    requested_by varchar(80) NOT NULL,
    approved_by varchar(80),
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    idempotency_key varchar(120) NOT NULL UNIQUE,
    request_hash varchar(64) NOT NULL,
    CONSTRAINT chk_payment_refund_status CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_payment_refund_sale ON payment_refund(source_sale_id);

CREATE TABLE external_document_request (
    id uuid PRIMARY KEY,
    document_type varchar(24) NOT NULL,
    source_id uuid NOT NULL,
    operation varchar(24) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    payload jsonb NOT NULL,
    payload_hash varchar(64) NOT NULL,
    provider_document_id varchar(160),
    provider_status varchar(80),
    provider_message varchar(2000),
    correlation_id varchar(160),
    submitted_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_document_source UNIQUE (document_type, source_id, operation)
);

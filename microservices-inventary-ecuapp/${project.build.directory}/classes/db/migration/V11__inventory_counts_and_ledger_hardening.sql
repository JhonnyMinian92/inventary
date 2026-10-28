-- P07: physical counts are append-only snapshots; stock changes are posted as compensating ledger movements.
CREATE TABLE inventory_count (
    id UUID PRIMARY KEY,
    count_number VARCHAR(48) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'POSTED', 'CANCELLED')),
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ,
    CONSTRAINT uk_inventory_count_number UNIQUE (count_number),
    CONSTRAINT uk_inventory_count_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_inventory_count_posted_at CHECK (
        (status = 'POSTED' AND posted_at IS NOT NULL) OR
        (status <> 'POSTED' AND posted_at IS NULL)
    )
);
CREATE INDEX ix_inventory_count_warehouse_created ON inventory_count(warehouse_id, created_at DESC);
CREATE INDEX ix_inventory_count_status_created ON inventory_count(status, created_at DESC);

CREATE TABLE inventory_count_line (
    id UUID PRIMARY KEY,
    inventory_count_id UUID NOT NULL REFERENCES inventory_count(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    expected_quantity NUMERIC(14,3) NOT NULL CHECK (expected_quantity >= 0),
    counted_quantity NUMERIC(14,3) NOT NULL CHECK (counted_quantity >= 0),
    unit_cost NUMERIC(14,2) NOT NULL CHECK (unit_cost >= 0),
    CONSTRAINT uk_inventory_count_line_product UNIQUE(inventory_count_id, product_id)
);
CREATE INDEX ix_inventory_count_line_product ON inventory_count_line(product_id, inventory_count_id);

-- Once captured, count lines must not be edited or deleted. A new count supersedes a prior count.
CREATE OR REPLACE FUNCTION prevent_inventory_count_line_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Physical count lines are immutable; create a new count to correct them.';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_inventory_count_line_immutable
    BEFORE UPDATE OR DELETE ON inventory_count_line
    FOR EACH ROW EXECUTE FUNCTION prevent_inventory_count_line_mutation();

-- V9 already installs append-only triggers for stock_movement and inventory_valuation_entry.
-- This migration intentionally adds no update path for stock_movement.

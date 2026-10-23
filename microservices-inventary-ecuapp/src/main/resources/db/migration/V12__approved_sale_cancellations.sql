-- P09: cancellation requests require explicit approval by a different actor.
ALTER TABLE sale DROP CONSTRAINT IF EXISTS sale_status_check;
ALTER TABLE sale DROP CONSTRAINT IF EXISTS ck_sale_status;
ALTER TABLE sale ADD CONSTRAINT ck_sale_status CHECK(status IN ('COMPLETED','CANCELLATION_REQUESTED','CANCELLED'));
ALTER TABLE sale ADD COLUMN cancellation_reason VARCHAR(1000);
ALTER TABLE sale ADD COLUMN cancellation_requested_by VARCHAR(80);
ALTER TABLE sale ADD COLUMN cancellation_requested_at TIMESTAMPTZ;
ALTER TABLE sale ADD COLUMN cancellation_approved_by VARCHAR(80);
ALTER TABLE sale ADD COLUMN cancellation_approved_at TIMESTAMPTZ;
-- Backfill legacy cancellations before enforcing the new consistency invariant.
UPDATE sale SET cancellation_reason='Cancelación histórica anterior a P09',
 cancellation_requested_by=created_by,cancellation_requested_at=sold_at,
 cancellation_approved_by=created_by,cancellation_approved_at=sold_at
 WHERE status='CANCELLED';
ALTER TABLE sale ADD CONSTRAINT ck_sale_cancellation_request_consistent CHECK (
 (status='COMPLETED' AND cancellation_requested_at IS NULL AND cancellation_approved_at IS NULL)
 OR (status='CANCELLATION_REQUESTED' AND cancellation_reason IS NOT NULL AND cancellation_requested_by IS NOT NULL AND cancellation_requested_at IS NOT NULL AND cancellation_approved_at IS NULL)
 OR (status='CANCELLED' AND cancellation_reason IS NOT NULL AND cancellation_requested_by IS NOT NULL AND cancellation_requested_at IS NOT NULL AND cancellation_approved_by IS NOT NULL AND cancellation_approved_at IS NOT NULL)
);
CREATE INDEX ix_sale_cancellation_pending ON sale(cancellation_requested_at DESC) WHERE status='CANCELLATION_REQUESTED';

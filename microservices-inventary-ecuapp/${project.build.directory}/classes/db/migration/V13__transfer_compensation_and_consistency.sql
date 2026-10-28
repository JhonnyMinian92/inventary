-- P10: durable compensation metadata and explicit reversed state for warehouse transfers.
ALTER TABLE stock_transfer DROP CONSTRAINT IF EXISTS stock_transfer_status_check;
ALTER TABLE stock_transfer DROP CONSTRAINT IF EXISTS ck_stock_transfer_status;
ALTER TABLE stock_transfer ADD CONSTRAINT ck_stock_transfer_status
 CHECK(status IN ('DRAFT','IN_TRANSIT','COMPLETED','CANCELLED','REVERSED'));

ALTER TABLE stock_transfer ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE stock_transfer ADD COLUMN cancelled_by VARCHAR(80);
ALTER TABLE stock_transfer ADD COLUMN cancellation_reason VARCHAR(1000);
ALTER TABLE stock_transfer ADD COLUMN reversed_at TIMESTAMPTZ;
ALTER TABLE stock_transfer ADD COLUMN reversed_by VARCHAR(80);
ALTER TABLE stock_transfer ADD COLUMN reversal_reason VARCHAR(1000);

-- Preserve auditability of legacy draft cancellations before enforcing new metadata invariants.
UPDATE stock_transfer
 SET cancelled_at=created_at,
     cancelled_by=created_by,
     cancellation_reason='Cancelación histórica anterior a P10'
 WHERE status='CANCELLED' AND cancelled_at IS NULL;

ALTER TABLE stock_transfer ADD CONSTRAINT ck_stock_transfer_terminal_metadata CHECK (
 (status IN ('DRAFT','IN_TRANSIT') AND cancelled_at IS NULL AND reversed_at IS NULL)
 OR (status='COMPLETED' AND cancelled_at IS NULL AND reversed_at IS NULL)
 OR (status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancellation_reason IS NOT NULL AND reversed_at IS NULL)
 OR (status='REVERSED' AND reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL AND cancelled_at IS NULL)
);

CREATE INDEX ix_stock_transfer_cancelled_at ON stock_transfer(cancelled_at DESC) WHERE status='CANCELLED';
CREATE INDEX ix_stock_transfer_reversed_at ON stock_transfer(reversed_at DESC) WHERE status='REVERSED';

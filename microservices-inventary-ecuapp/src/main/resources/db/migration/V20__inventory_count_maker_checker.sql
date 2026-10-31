-- Phase 2: record the operator who posts a physical count and enforce maker-checker.
-- Historical posted counts predate this invariant; their actual poster cannot be reconstructed.
ALTER TABLE inventory_count ADD COLUMN posted_by VARCHAR(80);
UPDATE inventory_count SET posted_by = 'LEGACY_UNATTRIBUTED' WHERE status = 'POSTED';
ALTER TABLE inventory_count
    ADD CONSTRAINT ck_inventory_count_posted_by
    CHECK (status <> 'POSTED' OR posted_by IS NOT NULL);

-- Phase 5: protect warehouse-establishment assignment closure from concurrent writes.
ALTER TABLE warehouse_establishment_assignment
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

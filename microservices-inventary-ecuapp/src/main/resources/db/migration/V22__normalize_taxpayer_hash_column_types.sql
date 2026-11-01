-- Align fixed-length hash columns with Hibernate's VARCHAR mapping.
-- V21 may already have been applied in environments that ran its first validation attempt.
ALTER TABLE taxpayer_profile
    ALTER COLUMN payload_hash TYPE VARCHAR(64) USING rtrim(payload_hash);
ALTER TABLE taxpayer_snapshot
    ALTER COLUMN payload_hash TYPE VARCHAR(64) USING rtrim(payload_hash);

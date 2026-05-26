ALTER TABLE audit_histories
    ADD COLUMN IF NOT EXISTS visibility_stage INTEGER,
    ADD COLUMN IF NOT EXISTS calculation_version VARCHAR(32);

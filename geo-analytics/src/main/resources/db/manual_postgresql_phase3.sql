-- Existing PostgreSQL DB: add columns missing before Hibernate ddl-auto=validate.
-- Brand-new DB: use one-time ddl-auto=create or generate DDL from entities, then switch to validate.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(16);
ALTER TABLE audit_histories ADD COLUMN IF NOT EXISTS overall_score INTEGER;

-- Existing PostgreSQL DB: add columns missing before Hibernate ddl-auto=validate.
-- Brand-new DB: use one-time ddl-auto=create or generate DDL from entities, then switch to validate.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(16);
-- Immutability after first non-null (snapshot plan). Allows NULL -> set on query registration; blocks later changes.
CREATE OR REPLACE FUNCTION jobs_prevent_applied_plan_change() RETURNS trigger AS $$
BEGIN
  IF OLD.subscription_plan IS NOT NULL AND NEW.subscription_plan IS DISTINCT FROM OLD.subscription_plan THEN
    RAISE EXCEPTION 'subscription_plan is immutable once set';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_jobs_applied_plan_immutable ON jobs;
CREATE TRIGGER trg_jobs_applied_plan_immutable BEFORE UPDATE ON jobs FOR EACH ROW EXECUTE FUNCTION jobs_prevent_applied_plan_change();
ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(16);
UPDATE workspaces SET subscription_plan = 'STANDARD' WHERE subscription_plan IS NULL;
ALTER TABLE audit_histories ADD COLUMN IF NOT EXISTS overall_score INTEGER;
ALTER TABLE audit_histories ADD COLUMN IF NOT EXISTS model_insights JSONB;
CREATE TABLE IF NOT EXISTS job_competitor_scores (
    id UUID PRIMARY KEY,
    audit_history_id UUID NOT NULL REFERENCES audit_histories(id) ON DELETE CASCADE,
    competitor_name VARCHAR(512) NOT NULL,
    som_score DOUBLE PRECISION NOT NULL,
    rank_position INTEGER,
    visibility_stage INTEGER,
    match_status VARCHAR(32) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_job_competitor_scores_audit ON job_competitor_scores(audit_history_id);

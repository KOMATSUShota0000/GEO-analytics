ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_diagnostic_message TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_recommended_actions JSONB;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS gap_batch_idempotency_key UUID;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS gap_analysis_gemini_job_name VARCHAR(512);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS gap_analysis_completed BOOLEAN NOT NULL DEFAULT FALSE;

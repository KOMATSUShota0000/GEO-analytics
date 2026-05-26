ALTER TABLE public.audit_rubric_results
    ADD COLUMN IF NOT EXISTS target_url VARCHAR(2048) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS is_self BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.audit_rubric_results_aud
    ADD COLUMN IF NOT EXISTS target_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS is_self BOOLEAN;

CREATE INDEX IF NOT EXISTS idx_audit_rubric_results_history_self
    ON public.audit_rubric_results (audit_history_id, is_self);
CREATE INDEX IF NOT EXISTS idx_audit_rubric_results_history_target
    ON public.audit_rubric_results (audit_history_id, target_url);

ALTER TABLE public.audit_histories
    ADD COLUMN IF NOT EXISTS job_recommended_actions JSONB;

ALTER TABLE public.audit_histories_aud
    ADD COLUMN IF NOT EXISTS job_recommended_actions JSONB;

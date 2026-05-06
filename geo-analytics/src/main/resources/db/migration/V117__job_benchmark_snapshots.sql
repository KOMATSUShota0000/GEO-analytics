ALTER TABLE public.jobs
    ADD COLUMN IF NOT EXISTS self_rubric_audit_json JSONB,
    ADD COLUMN IF NOT EXISTS competitor_rubric_audits_json JSONB,
    ADD COLUMN IF NOT EXISTS self_crawled_page_json JSONB,
    ADD COLUMN IF NOT EXISTS meo_review_count INTEGER,
    ADD COLUMN IF NOT EXISTS meo_average_stars DOUBLE PRECISION;

ALTER TABLE public.jobs_aud
    ADD COLUMN IF NOT EXISTS self_rubric_audit_json JSONB,
    ADD COLUMN IF NOT EXISTS competitor_rubric_audits_json JSONB,
    ADD COLUMN IF NOT EXISTS self_crawled_page_json JSONB,
    ADD COLUMN IF NOT EXISTS meo_review_count INTEGER,
    ADD COLUMN IF NOT EXISTS meo_average_stars DOUBLE PRECISION;

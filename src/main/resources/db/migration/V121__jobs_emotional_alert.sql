ALTER TABLE public.jobs
    ADD COLUMN IF NOT EXISTS emotional_alert JSONB;

ALTER TABLE public.jobs_aud
    ADD COLUMN IF NOT EXISTS emotional_alert JSONB;

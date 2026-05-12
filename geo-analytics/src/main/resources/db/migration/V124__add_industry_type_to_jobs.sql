ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS industry_type VARCHAR(32);

UPDATE public.jobs
SET industry_type = 'LOCAL_STORE'
WHERE industry_type IS NULL;

ALTER TABLE public.jobs
    ALTER COLUMN industry_type SET DEFAULT 'LOCAL_STORE';

ALTER TABLE public.jobs
    ALTER COLUMN industry_type SET NOT NULL;

ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS industry_type VARCHAR(32);

UPDATE public.jobs_aud
SET industry_type = 'LOCAL_STORE'
WHERE industry_type IS NULL;

ALTER TABLE public.jobs_aud
    ALTER COLUMN industry_type SET DEFAULT 'LOCAL_STORE';

ALTER TABLE public.jobs_aud
    ALTER COLUMN industry_type SET NOT NULL;

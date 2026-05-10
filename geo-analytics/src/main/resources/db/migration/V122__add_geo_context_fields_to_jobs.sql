ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS target_url VARCHAR(2048);
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS business_summary TEXT;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS target_audience TEXT;
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS focus_points TEXT;

UPDATE public.jobs j
SET target_url = p.target_url
FROM public.projects p
WHERE j.project_id = p.id
  AND (j.target_url IS NULL OR btrim(j.target_url) = '');

UPDATE public.jobs
SET target_url = 'https://placeholder.invalid/geo-job-missing-project'
WHERE target_url IS NULL OR btrim(target_url) = '';

ALTER TABLE public.jobs
    ALTER COLUMN target_url SET NOT NULL;

ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS target_url VARCHAR(2048);
ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS business_summary TEXT;
ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS target_audience TEXT;
ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS focus_points TEXT;

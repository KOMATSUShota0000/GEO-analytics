ALTER TABLE public.jobs
    ALTER COLUMN job_status TYPE VARCHAR(32);

ALTER TABLE public.jobs_aud
    ALTER COLUMN job_status TYPE VARCHAR(32);

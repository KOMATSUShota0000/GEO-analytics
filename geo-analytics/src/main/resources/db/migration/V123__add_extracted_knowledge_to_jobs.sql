ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS extracted_knowledge TEXT;

ALTER TABLE public.jobs_aud ADD COLUMN IF NOT EXISTS extracted_knowledge TEXT;

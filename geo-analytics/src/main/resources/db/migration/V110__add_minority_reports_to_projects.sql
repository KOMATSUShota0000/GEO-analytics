ALTER TABLE public.projects
    ADD COLUMN minority_reports JSONB NOT NULL DEFAULT '[]'::jsonb;

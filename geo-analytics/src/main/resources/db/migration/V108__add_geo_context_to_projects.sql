ALTER TABLE public.projects
    ADD COLUMN industry_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN extracted_strengths TEXT,
    ADD COLUMN target_audience TEXT;
COMMENT ON COLUMN public.projects.industry_type IS '業種分類（IndustryTypeの列挙子名。既存行はOTHER）';
COMMENT ON COLUMN public.projects.extracted_strengths IS '自社サイト解析等で抽出した強み';
COMMENT ON COLUMN public.projects.target_audience IS '想定ターゲット層';
ALTER TABLE public.projects_aud
    ADD COLUMN industry_type VARCHAR(32),
    ADD COLUMN extracted_strengths TEXT,
    ADD COLUMN target_audience TEXT;

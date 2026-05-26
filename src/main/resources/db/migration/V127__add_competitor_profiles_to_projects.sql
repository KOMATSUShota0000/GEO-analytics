-- 競合表示の正直化（参考基準点バッジ）のための追加列。
-- 既存の project_competitors（URL文字列）は非破壊で維持し、名称・URL・合成フラグを
-- まとめて保持する competitor_profiles を追加する。既存行はデフォルト空配列で後方互換。
ALTER TABLE public.projects
    ADD COLUMN competitor_profiles JSONB NOT NULL DEFAULT '[]'::jsonb;

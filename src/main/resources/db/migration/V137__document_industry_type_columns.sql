-- Why: jobs.industry_type と projects.industry_type は同名だが別の値集合を持つ。
--        * jobs.industry_type    -> BusinessModelType（LOCAL_STORE / CORPORATE_SERVICE / ONLINE_SERVICE）
--        * projects.industry_type -> IndustryType（YMYL / LOCAL / B2B / B2C / EC / OTHER）
--      C5-e で enum を CompetitorExtractionMode から BusinessModelType へ改名したが、列名は
--      API のワイヤ名（industryType）と揃っており、変更すると互換性を壊すため据え置いた。
--      代わりに列コメントで差異を明示し、dbdoc（tbls）にも載るようにする。スキーマ変更は無い。

COMMENT ON COLUMN public.jobs.industry_type IS
    '事業形態 BusinessModelType（LOCAL_STORE / CORPORATE_SERVICE / ONLINE_SERVICE）。'
    '権威スコアの配分（第三者言及と MEO の比重）を切り替える。'
    'projects.industry_type の IndustryType とは別の値集合なので混同しないこと。';

COMMENT ON COLUMN public.projects.industry_type IS
    '業種 IndustryType（YMYL / LOCAL / B2B / B2C / EC / OTHER）。'
    'IndustryTypeBusinessModelMapper が jobs.industry_type の BusinessModelType へ粗くマップする。';

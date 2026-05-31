-- ジョブ全体アドバイスの生成元（AI / TEMPLATE_FALLBACK）。
-- フロントの「簡易分析モード」バッジ（仕様書 F-3.1）とフォールバック発動率集計（N-4）に用いる。
-- 既存行は NULL（生成元不明）のままで、フロントは NULL を「バッジなし」として扱う。
ALTER TABLE public.jobs ADD COLUMN IF NOT EXISTS job_advice_source VARCHAR(32);

COMMENT ON COLUMN public.jobs.job_advice_source IS 'ジョブ全体アドバイスの生成元（AdviceSource: AI / TEMPLATE_FALLBACK）';

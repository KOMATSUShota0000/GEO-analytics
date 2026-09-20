-- 競合の実測値（AI回答に同時に登場した他ブランド）の保存先（#112）。
-- Why: 専用テーブル job_competitor_scores は V133（ADR-034）で「書き込み専用・読み出しゼロの死蔵」として
--      撤去済み。今回は読み出す側（競合シェア円グラフ）ができたが、表を復活させる必要は無い。
--      競合の行は audit_histories の1行に完全従属し、独立した検索も更新もしない。列として持てば
--      テナント隔離は audit_histories の RLS がそのまま効き、ポリシーの二重管理も起きない（#45 の教訓）。
-- 形: [{competitorLabel, somScore, aiCitationPosition, mentionCount}]
ALTER TABLE audit_histories
    ADD COLUMN IF NOT EXISTS competitor_results JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN audit_histories.competitor_results IS
    'AI回答に同時に登場した他ブランドの実測値。名前は LLM が挙げ、選別と計数は Java が行う（#64 / #65 / #112）';

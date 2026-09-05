-- Why: 競合比較機能の段階的廃止（ADR-031〜034）の C5-b。読み出しゼロの死蔵であることを
--      確認済みのため撤去する（不可逆・オーナー承認済み）。
--        * project_competitors: 0 行。書き込み経路は空リスト設定のみで、非空を書いていた
--          唯一の経路 saveProjectCompetitorUrls は C5-a で削除済み。
--        * projects.competitor_profiles: '[]' 以外 0 行。
--      ADR-034 で job_competitor_scores を DROP したのと同じ判断。
-- Why: project_competitors には主キーが無く、同一の (project_id, competitor_url) を重複
--      挿入できる状態だった。テーブルごと消えるため制約追加ではなく撤去で解消する。

DROP TABLE IF EXISTS public.project_competitors;

ALTER TABLE public.projects
    DROP COLUMN IF EXISTS competitor_profiles;

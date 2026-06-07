-- 競合「特定・表示」機能の廃止（ADR-031〜034）に伴い、書き込み専用で読み出しゼロの
-- 死蔵テーブル job_competitor_scores を撤去する。GBVS相対スコアはメモリ内集計
-- （InformationTheoryBasedAggregator / CompetitorResult）で算出され本テーブルに依存しない。
DROP TABLE IF EXISTS job_competitor_scores;

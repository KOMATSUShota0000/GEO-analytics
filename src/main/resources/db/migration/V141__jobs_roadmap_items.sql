-- 解析ごとの改善ロードマップ（どの順番で・どのフェーズでやるか）を持つ（#77）。
-- Why: 改善タスク（何をやるか）とは別の成果物で、フェーズ順を持つことが本質。従来はプロンプトの
--      任意フィールドとして GBVS の1コールに相乗りしており、実測8件すべてで省略されて機能していなかった。
--      4ペルソナ議論の DIRECTOR の成果物として出す（オーナー確定 2026-09-19）。
-- minority_reports（V140）と同じく、既存行を null 分岐なしで扱えるよう空配列を既定値とする。
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS roadmap_items JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN jobs.roadmap_items IS
    '改善ロードマップ: [{phase, title, rationale, expectedImpact}]。phase は NOW / SHORT_TERM / MID_TERM（#77）';

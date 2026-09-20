-- 解析単位のマイノリティ・レポート（議論で合意に至らなかったが捨てるに惜しい尖った提案）を持つ（#80）。
-- Why: 型（MinorityReport）と DTO は既にあるが、解析単位の保存先が無かった。プロジェクト単位のもの
--      （V110 / projects.minority_reports）はオンボーディングの見立てで別物。jobs は既にテナント列と
--      RLS を持つため、ここに置けば隔離は既存ポリシーがそのまま効く。
-- 既存行と未生成ジョブを null 分岐なしで扱えるよう、projects と同じく空配列を既定値とする。
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS minority_reports JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN jobs.minority_reports IS
    '解析ごとのマイノリティ・レポート: [{insight, conflictReason, evidence}]。合意案に入らなかった尖った提案（#80）';

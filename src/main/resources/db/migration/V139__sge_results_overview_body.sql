-- AI Overview の本文を列として持つ（#94 / ADR-046）。
-- Why: バッチ経路は「投入」と「回収」が別のタイミングで走る。生JSONから毎回抽出し直すと、
--      SerpAPI の応答形式が変わったときに投入時と回収時で判断が食い違う。
ALTER TABLE sge_results
    ADD COLUMN IF NOT EXISTS overview_body TEXT;

COMMENT ON COLUMN sge_results.overview_body IS
    'AI Overview の本文。空なら実測が取れなかったクエリ（材料は推定になる）';

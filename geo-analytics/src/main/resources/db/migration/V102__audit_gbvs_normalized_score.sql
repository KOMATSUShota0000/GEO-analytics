-- GBVS 正規化スコアを SOM と独立して保持する。パース失敗時は som_score を NULL 許容にする。
ALTER TABLE audit_histories
    ADD COLUMN IF NOT EXISTS gbvs_normalized_score NUMERIC;

ALTER TABLE audit_histories
    ALTER COLUMN som_score DROP NOT NULL;

ALTER TABLE audit_histories_aud
    ADD COLUMN IF NOT EXISTS gbvs_normalized_score NUMERIC;

ALTER TABLE audit_histories_aud
    ALTER COLUMN som_score DROP NOT NULL;

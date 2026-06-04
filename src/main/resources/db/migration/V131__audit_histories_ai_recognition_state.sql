-- AIがブランドを正しい実体として認識しているかの定性ステート（V13_GEO4AXIS / Sprint3）。
-- スコア非算入のレポート用エビデンス。enum 名（RECOGNIZED_CORRECTLY/MISIDENTIFIED/UNKNOWN）を格納する。
ALTER TABLE audit_histories
    ADD COLUMN IF NOT EXISTS ai_recognition_state VARCHAR(32);

ALTER TABLE audit_histories_aud
    ADD COLUMN IF NOT EXISTS ai_recognition_state VARCHAR(32);

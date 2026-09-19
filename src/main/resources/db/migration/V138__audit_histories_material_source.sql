-- 検証に使った材料の出どころ（実測の AI Overview か、LLM 生成の推定か）を行ごとに残す（#92 / ADR-039）。
-- 既存行と、まだ切り替えていないバッチ経路の行は正直に ESTIMATED として扱う。
ALTER TABLE audit_histories
    ADD COLUMN IF NOT EXISTS material_source VARCHAR(16) NOT NULL DEFAULT 'ESTIMATED';

ALTER TABLE audit_histories
    DROP CONSTRAINT IF EXISTS chk_audit_histories_material_source;

ALTER TABLE audit_histories
    ADD CONSTRAINT chk_audit_histories_material_source
        CHECK (material_source IN ('MEASURED', 'ESTIMATED'));

COMMENT ON COLUMN audit_histories.material_source IS
    '検証の材料: MEASURED=実測のAI Overview本文 / ESTIMATED=LLM生成の回答文';

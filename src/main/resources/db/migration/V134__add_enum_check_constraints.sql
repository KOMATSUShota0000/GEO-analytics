-- Why: enum 由来の列は V2 で PostgreSQL ENUM 型から VARCHAR へ退避した際、DB 側の値検証を
--      失った（退避の原因は role を WHERE 句で比較する派生クエリの型不一致。ENUM 型に戻すと
--      同じ問題を再発させるため、VARCHAR + CHECK で二重制約とする）。
--      Java enum との同期は EnumCheckConstraintSyncTest が CI で機械的に強制する。
-- Why: NULL 許容列も `col IN (...)` で足りる。NULL IN (...) は NULL に評価され、CHECK 制約は
--      結果が FALSE のときだけ違反となるため NULL は素通りする。

ALTER TABLE public.organization_users
    ADD CONSTRAINT chk_organization_users_role
    CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER'));

ALTER TABLE public.jobs
    ADD CONSTRAINT chk_jobs_job_status
    CHECK (job_status IN ('CREATED', 'EXTRACTING_COMPETITORS', 'REALTIME_PROCESSING',
                          'FILE_UPLOADED', 'SUBMITTED', 'RUNNING', 'COMPLETED', 'FAILED'));

-- Why: 列名は industry_type だが JobEntity のマッピング先は CompetitorExtractionMode であり、
--      projects.industry_type（IndustryType）とは別の値集合を持つ。ADR-034 の残作業 C5
--      （CompetitorExtractionMode→業種名リネーム）が完了するまで、この差異を制約で明文化する。
ALTER TABLE public.jobs
    ADD CONSTRAINT chk_jobs_industry_type
    CHECK (industry_type IN ('LOCAL_STORE', 'CORPORATE_SERVICE', 'ONLINE_SERVICE'));

ALTER TABLE public.jobs
    ADD CONSTRAINT chk_jobs_subscription_plan
    CHECK (subscription_plan IN ('STANDARD', 'PRO', 'EXPERT'));

ALTER TABLE public.workspaces
    ADD CONSTRAINT chk_workspaces_subscription_plan
    CHECK (subscription_plan IN ('STANDARD', 'PRO', 'EXPERT'));

ALTER TABLE public.projects
    ADD CONSTRAINT chk_projects_industry_type
    CHECK (industry_type IN ('YMYL', 'LOCAL', 'B2B', 'B2C', 'EC', 'OTHER'));

-- Why: amount >= 0 の CHECK が既にあるとおり、増減の方向は符号ではなく transaction_type が決める。
--      この列が壊れるとチケット残高の計算が壊れるため、金額と同等の防御を課す。
ALTER TABLE public.wallet_transactions
    ADD CONSTRAINT chk_wallet_transactions_transaction_type
    CHECK (transaction_type IN ('RESERVE', 'SETTLE', 'REFUND'));

ALTER TABLE public.audit_histories
    ADD CONSTRAINT chk_audit_histories_ai_recognition_state
    CHECK (ai_recognition_state IN ('RECOGNIZED_CORRECTLY', 'MISIDENTIFIED', 'UNKNOWN'));

ALTER TABLE public.project_keywords
    ADD CONSTRAINT chk_project_keywords_analysis_priority
    CHECK (analysis_priority IN ('HIGH', 'NORMAL'));

ALTER TABLE public.project_keywords
    ADD CONSTRAINT chk_project_keywords_preferred_engine
    CHECK (preferred_engine IN ('AI_OVERVIEW'));

ALTER TABLE public.rag_domain_rules
    ADD CONSTRAINT chk_rag_domain_rules_rule_kind
    CHECK (rule_kind IN ('TRUST_BOOST', 'BLOCK_ANALYSIS', 'ALLOW_NON_JP'));

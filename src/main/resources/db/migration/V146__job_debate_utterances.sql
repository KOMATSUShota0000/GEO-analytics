-- 解析ごとの4人のAIの議論の発言を、話した順に1件ずつ残す（#197）。
-- Why: 結果画面の待ち時間に議論を1発言ずつ見せ、終わったあとも読み返せるようにする（#194）。
--      議論用の本文は長く専門用語も混ざるため持たず、画面用の要約・返事の相手・根拠だけを持つ（ADR-094）。
--      同じジョブで議論をやり直したとき・議論が失敗したときは、そのジョブの発言を消す（2026-09-30 オーナー確定）。
CREATE TABLE public.job_debate_utterances (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES public.jobs (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES public.organizations (id) ON DELETE CASCADE,
    seq SMALLINT NOT NULL,
    round SMALLINT NULL,
    speaker VARCHAR(16) NOT NULL,
    summary TEXT NOT NULL,
    reply_to VARCHAR(16) NULL,
    stance VARCHAR(32) NULL,
    evidence_kind VARCHAR(32) NULL,
    evidence_task_number SMALLINT NULL,
    evidence_detail TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_job_debate_utterances_job_seq UNIQUE (job_id, seq)
);
CREATE INDEX idx_job_debate_utterances_org ON public.job_debate_utterances (organization_id);

-- 議論の状態。NULL はこの列より前の解析で、発言が保存されていない。
ALTER TABLE public.jobs ADD COLUMN debate_status VARCHAR(16) NULL;
ALTER TABLE public.jobs
    ADD CONSTRAINT chk_jobs_debate_status CHECK (debate_status IN ('RUNNING', 'COMPLETED', 'SKIPPED', 'FAILED'));

-- 書き込むのは裏の処理（batch_worker）だけで、画面は読むだけ。既定の権限（V3）で付く分を外してから付け直す。
REVOKE ALL ON TABLE public.job_debate_utterances FROM api_worker, batch_worker;
GRANT SELECT ON TABLE public.job_debate_utterances TO api_worker;
GRANT SELECT, INSERT, DELETE ON TABLE public.job_debate_utterances TO batch_worker;

-- Why: jobs の RLS と同じく組織単位で隔離する。組織IDを列に持たせ、行ごとに workspaces を引かずに済ませる。
ALTER TABLE public.job_debate_utterances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_debate_utterances FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS job_debate_utterances_api_worker_policy ON public.job_debate_utterances;
CREATE POLICY job_debate_utterances_api_worker_policy ON public.job_debate_utterances
    FOR SELECT TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

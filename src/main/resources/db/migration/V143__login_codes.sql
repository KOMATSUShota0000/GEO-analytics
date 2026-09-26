-- メールに届く6桁のコードでのログイン（#144 / #146）。
-- ユーザーごとに「今有効なコード」を1行だけ持つ（user_id が主キー）。新しいコードは同じ行を上書きするので、
-- 古いコードは自動で無効になり、同時に要求されても有効なコードが2つできない。
-- コードそのものは保存せず、サーバー側の鍵で作った HMAC-SHA256（16進64文字）で持つ。

CREATE TABLE public.login_codes (
    user_id UUID PRIMARY KEY REFERENCES public.organization_users (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES public.organizations (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_login_codes_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_login_codes_expiry CHECK (expires_at > issued_at)
);
CREATE INDEX idx_login_codes_org ON public.login_codes (organization_id);

-- ログインは API からだけ行う。batch_worker は RLS を素通りする（V3 の BYPASSRLS）うえ、
-- 既定の権限（V3 の ALTER DEFAULT PRIVILEGES）で自動的に権限が付くため、明示的に外す。
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.login_codes TO api_worker;
REVOKE ALL ON TABLE public.login_codes FROM batch_worker;
ALTER TABLE public.login_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.login_codes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS login_codes_api_worker_policy ON public.login_codes;
CREATE POLICY login_codes_api_worker_policy ON public.login_codes
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

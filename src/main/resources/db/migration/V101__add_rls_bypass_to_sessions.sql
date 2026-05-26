-- システム経路（@GlobalAccess）専用: app.rls_bypass = 'on' のとき user_sessions の RLS を緩和する。
-- アプリはトランザクションローカルな set_config(..., true) のみでフラグを立て、コミットで消える。

DROP POLICY IF EXISTS user_sessions_api_worker_policy ON public.user_sessions;

CREATE POLICY user_sessions_api_worker_policy ON public.user_sessions
    FOR ALL TO api_worker
    USING (
        (
            organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
            AND deleted_at IS NULL
        )
        OR current_setting('app.rls_bypass', true) = 'on'
    )
    WITH CHECK (
        (
            organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        )
        OR current_setting('app.rls_bypass', true) = 'on'
    );

DROP POLICY IF EXISTS organization_users_auth_select_policy ON public.organization_users;
CREATE POLICY organization_users_auth_select_policy ON public.organization_users
    FOR SELECT TO api_worker
    USING (
        NULLIF(current_setting('app.current_org_id', true), '') IS NULL
        AND deleted_at IS NULL
    );

DROP POLICY IF EXISTS user_sessions_auth_select_policy ON public.user_sessions;
CREATE POLICY user_sessions_auth_select_policy ON public.user_sessions
    FOR SELECT TO api_worker
    USING (
        NULLIF(current_setting('app.current_org_id', true), '') IS NULL
        AND deleted_at IS NULL
    );

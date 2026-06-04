-- Stripe セルフサーブ自動決済（案B）の永続化。
-- 1) workspaces に Stripe 顧客/サブスクID列を追加（後方互換のため NULL 許容）。
-- 2) Webhook の冪等台帳 processed_stripe_events を追加（RLS はテナント隔離方針に準拠）。

ALTER TABLE public.workspaces
    ADD COLUMN stripe_customer_id VARCHAR(64),
    ADD COLUMN stripe_subscription_id VARCHAR(64);

CREATE INDEX idx_workspaces_stripe_subscription
    ON public.workspaces (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;

CREATE TABLE public.processed_stripe_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations (id) ON DELETE CASCADE,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_stripe_event UNIQUE (event_id)
);
CREATE INDEX idx_processed_stripe_events_org
    ON public.processed_stripe_events (organization_id, processed_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.processed_stripe_events TO api_worker, batch_worker;
ALTER TABLE public.processed_stripe_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.processed_stripe_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS processed_stripe_events_api_worker_policy ON public.processed_stripe_events;
CREATE POLICY processed_stripe_events_api_worker_policy ON public.processed_stripe_events
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

CREATE TABLE public.wallet_transactions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations (id) ON DELETE RESTRICT,
    project_id UUID REFERENCES public.projects (id) ON DELETE SET NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    parent_reservation_id UUID REFERENCES public.wallet_transactions (id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_tx_amount_pos CHECK (amount >= 0)
);
CREATE UNIQUE INDEX uq_parent_reservation ON public.wallet_transactions (parent_reservation_id) WHERE (parent_reservation_id IS NOT NULL);
CREATE INDEX idx_wallet_tx_org ON public.wallet_transactions (organization_id, created_at);
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.wallet_transactions TO api_worker, batch_worker;
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_transactions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS wallet_transactions_api_worker_policy ON public.wallet_transactions;
CREATE POLICY wallet_transactions_api_worker_policy ON public.wallet_transactions
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

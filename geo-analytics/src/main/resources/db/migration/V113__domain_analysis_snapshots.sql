-- GEO query proposal: persisted AI domain analysis (tenant-scoped, Envers-audited)
CREATE TABLE public.domain_analysis_snapshots (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    source_url TEXT NOT NULL,
    inferred_persona TEXT NOT NULL,
    queries JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_domain_analysis_snapshots_tenant_created
    ON public.domain_analysis_snapshots (tenant_id, created_at DESC);

CREATE TABLE public.domain_analysis_snapshots_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    source_url TEXT,
    inferred_persona TEXT,
    queries JSONB,
    created_at TIMESTAMP,
    CONSTRAINT fk_domain_analysis_snapshots_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_domain_analysis_snapshots_aud PRIMARY KEY (rev, id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.domain_analysis_snapshots TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.domain_analysis_snapshots_aud TO api_worker, batch_worker;

ALTER TABLE public.domain_analysis_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.domain_analysis_snapshots FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS domain_analysis_snapshots_api_worker_policy ON public.domain_analysis_snapshots;
CREATE POLICY domain_analysis_snapshots_api_worker_policy ON public.domain_analysis_snapshots
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = domain_analysis_snapshots.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = domain_analysis_snapshots.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.domain_analysis_snapshots_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.domain_analysis_snapshots_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS domain_analysis_snapshots_aud_api_worker_policy ON public.domain_analysis_snapshots_aud;
CREATE POLICY domain_analysis_snapshots_aud_api_worker_policy ON public.domain_analysis_snapshots_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = domain_analysis_snapshots_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = domain_analysis_snapshots_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

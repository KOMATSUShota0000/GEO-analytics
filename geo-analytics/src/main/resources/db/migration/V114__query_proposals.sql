-- Query proposal aggregate: tenant-scoped parent/child, Envers-audited
CREATE TABLE public.query_proposals (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    url VARCHAR(2083) NOT NULL,
    business_description TEXT NOT NULL,
    target_audience TEXT NOT NULL,
    strategic_focus TEXT NOT NULL,
    inferred_persona TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_proposals_tenant_created
    ON public.query_proposals (tenant_id, created_at DESC);

CREATE TABLE public.query_proposal_suggested_queries (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    proposal_id UUID NOT NULL,
    query_text TEXT NOT NULL,
    intent TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_query_proposal_suggested_queries_proposal
        FOREIGN KEY (proposal_id) REFERENCES public.query_proposals (id) ON DELETE CASCADE
);

CREATE INDEX idx_query_proposal_suggested_queries_proposal
    ON public.query_proposal_suggested_queries (proposal_id);

CREATE INDEX idx_query_proposal_suggested_queries_tenant_created
    ON public.query_proposal_suggested_queries (tenant_id, proposal_id);

CREATE TABLE public.query_proposals_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    url VARCHAR(2083),
    business_description TEXT,
    target_audience TEXT,
    strategic_focus TEXT,
    inferred_persona TEXT,
    created_at TIMESTAMP,
    CONSTRAINT fk_query_proposals_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_query_proposals_aud PRIMARY KEY (rev, id)
);

CREATE TABLE public.query_proposal_suggested_queries_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    proposal_id UUID,
    query_text TEXT,
    intent TEXT,
    sort_order INTEGER,
    CONSTRAINT fk_query_proposal_suggested_queries_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_query_proposal_suggested_queries_aud PRIMARY KEY (rev, id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.query_proposals TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.query_proposal_suggested_queries TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.query_proposals_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.query_proposal_suggested_queries_aud TO api_worker, batch_worker;

ALTER TABLE public.query_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.query_proposals FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS query_proposals_api_worker_policy ON public.query_proposals;
CREATE POLICY query_proposals_api_worker_policy ON public.query_proposals
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposals.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposals.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.query_proposal_suggested_queries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.query_proposal_suggested_queries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS query_proposal_suggested_queries_api_worker_policy ON public.query_proposal_suggested_queries;
CREATE POLICY query_proposal_suggested_queries_api_worker_policy ON public.query_proposal_suggested_queries
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposal_suggested_queries.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposal_suggested_queries.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.query_proposals_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.query_proposals_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS query_proposals_aud_api_worker_policy ON public.query_proposals_aud;
CREATE POLICY query_proposals_aud_api_worker_policy ON public.query_proposals_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposals_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposals_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.query_proposal_suggested_queries_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.query_proposal_suggested_queries_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS query_proposal_suggested_queries_aud_api_worker_policy ON public.query_proposal_suggested_queries_aud;
CREATE POLICY query_proposal_suggested_queries_aud_api_worker_policy ON public.query_proposal_suggested_queries_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposal_suggested_queries_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = query_proposal_suggested_queries_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

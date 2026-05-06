CREATE TABLE public.audit_rubric_results (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    audit_history_id UUID NOT NULL,
    criterion_id VARCHAR(64) NOT NULL,
    verdict VARCHAR(16) NOT NULL,
    evidence TEXT,
    score NUMERIC(7,3) NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_rubric_results_tenant_history
    ON public.audit_rubric_results (tenant_id, audit_history_id);
CREATE INDEX idx_audit_rubric_results_history_criterion
    ON public.audit_rubric_results (audit_history_id, criterion_id);

CREATE TABLE public.audit_rubric_results_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    audit_history_id UUID,
    criterion_id VARCHAR(64),
    verdict VARCHAR(16),
    evidence TEXT,
    score NUMERIC(7,3),
    CONSTRAINT fk_audit_rubric_results_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_audit_rubric_results_aud PRIMARY KEY (rev, id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.audit_rubric_results TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.audit_rubric_results_aud TO api_worker, batch_worker;

ALTER TABLE public.audit_rubric_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_rubric_results FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_rubric_results_api_worker_policy ON public.audit_rubric_results;
CREATE POLICY audit_rubric_results_api_worker_policy ON public.audit_rubric_results
    FOR ALL TO api_worker
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    );

ALTER TABLE public.audit_rubric_results_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_rubric_results_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_rubric_results_aud_api_worker_policy ON public.audit_rubric_results_aud;
CREATE POLICY audit_rubric_results_aud_api_worker_policy ON public.audit_rubric_results_aud
    FOR ALL TO api_worker
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    );

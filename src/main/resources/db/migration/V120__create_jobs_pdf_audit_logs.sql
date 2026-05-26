CREATE TABLE public.jobs_pdf_audit_logs (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    organization_id UUID NOT NULL,
    job_id UUID NOT NULL,
    actor_user_id UUID,
    actor_session_id UUID,
    pdf_byte_size BIGINT NOT NULL DEFAULT 0,
    report_kind VARCHAR(32) NOT NULL DEFAULT 'JOB_REPORT',
    exported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_pdf_audit_tenant_job
    ON public.jobs_pdf_audit_logs (tenant_id, job_id);
CREATE INDEX idx_jobs_pdf_audit_tenant_exported_at
    ON public.jobs_pdf_audit_logs (tenant_id, exported_at DESC);

GRANT SELECT, INSERT ON TABLE public.jobs_pdf_audit_logs TO api_worker, batch_worker;

REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.jobs_pdf_audit_logs FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.jobs_pdf_audit_logs FROM api_worker;
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.jobs_pdf_audit_logs FROM batch_worker;

ALTER TABLE public.jobs_pdf_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jobs_pdf_audit_logs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS jobs_pdf_audit_logs_api_worker_policy ON public.jobs_pdf_audit_logs;
CREATE POLICY jobs_pdf_audit_logs_api_worker_policy ON public.jobs_pdf_audit_logs
    FOR ALL TO api_worker
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    );

DROP POLICY IF EXISTS jobs_pdf_audit_logs_batch_worker_policy ON public.jobs_pdf_audit_logs;
CREATE POLICY jobs_pdf_audit_logs_batch_worker_policy ON public.jobs_pdf_audit_logs
    FOR ALL TO batch_worker
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')
    );

CREATE OR REPLACE FUNCTION public.fn_jobs_pdf_audit_logs_worm()
    RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'jobs_pdf_audit_logs is append-only (WORM): % is not permitted', TG_OP
        USING ERRCODE = 'feature_not_supported';
END;
$$;

DROP TRIGGER IF EXISTS trg_jobs_pdf_audit_logs_no_update ON public.jobs_pdf_audit_logs;
CREATE TRIGGER trg_jobs_pdf_audit_logs_no_update
    BEFORE UPDATE ON public.jobs_pdf_audit_logs
    FOR EACH ROW EXECUTE FUNCTION public.fn_jobs_pdf_audit_logs_worm();

DROP TRIGGER IF EXISTS trg_jobs_pdf_audit_logs_no_delete ON public.jobs_pdf_audit_logs;
CREATE TRIGGER trg_jobs_pdf_audit_logs_no_delete
    BEFORE DELETE ON public.jobs_pdf_audit_logs
    FOR EACH ROW EXECUTE FUNCTION public.fn_jobs_pdf_audit_logs_worm();

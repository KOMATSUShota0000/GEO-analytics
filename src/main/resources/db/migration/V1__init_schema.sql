CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION jobs_prevent_applied_plan_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.subscription_plan IS NOT NULL AND NEW.subscription_plan IS DISTINCT FROM OLD.subscription_plan THEN
        RAISE EXCEPTION 'subscription_plan is immutable once set';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TYPE user_role AS ENUM ('ADMIN', 'MEMBER', 'VIEWER');

CREATE TABLE plans (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    monthly_price BIGINT NOT NULL,
    monthly_credits BIGINT NOT NULL,
    keyword_limit INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TRIGGER trg_plans_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(512) NOT NULL,
    plan_id VARCHAR(20) NOT NULL,
    credit_balance BIGINT NOT NULL DEFAULT 0,
    billing_cycle_anchor TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_organizations_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE organization_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_organization_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT uq_organization_users_email UNIQUE (email)
);

CREATE TRIGGER trg_organization_users_updated_at
    BEFORE UPDATE ON organization_users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_organization_users_org_active ON organization_users (organization_id) WHERE deleted_at IS NULL;

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_tenants_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_tenants_organization_active ON tenants (organization_id) WHERE deleted_at IS NULL;

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_user_sessions_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES organization_users (id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_user_sessions_updated_at
    BEFORE UPDATE ON user_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX uq_user_sessions_session_id_active ON user_sessions (session_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_user_sessions_user_active ON user_sessions (user_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_user_sessions_expires_at_active ON user_sessions (expires_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_user_sessions_organization_active ON user_sessions (organization_id) WHERE deleted_at IS NULL;

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    subscription_plan VARCHAR(16),
    organization_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_workspaces_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_workspaces_updated_at
    BEFORE UPDATE ON workspaces
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(320) NOT NULL,
    password VARCHAR(255),
    role VARCHAR(32) NOT NULL,
    pricing_plan VARCHAR(32) NOT NULL,
    webauthn_credential_id BYTEA,
    webauthn_public_key_cose BYTEA,
    webauthn_signature_count BIGINT NOT NULL,
    webauthn_transports VARCHAR(256),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    target_url VARCHAR(255) NOT NULL,
    brand_color VARCHAR(64) NOT NULL,
    logo_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    auto_audit_enabled BOOLEAN NOT NULL,
    slack_webhook_url VARCHAR(2048),
    notification_email VARCHAR(320),
    last_audit_at TIMESTAMP
);

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE project_competitors (
    project_id UUID NOT NULL,
    competitor_url VARCHAR(2048) NOT NULL,
    CONSTRAINT fk_project_competitors_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE project_keywords (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    project_id UUID NOT NULL,
    keyword_text TEXT NOT NULL,
    analysis_priority VARCHAR(16) NOT NULL,
    preferred_engine VARCHAR(32) NOT NULL,
    CONSTRAINT fk_project_keywords_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_project_keywords_project_text UNIQUE (project_id, keyword_text)
);

CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    project_id UUID,
    job_status VARCHAR(20) NOT NULL,
    subscription_plan VARCHAR(16),
    plan_limits_snapshot JSONB,
    brand_name VARCHAR(255) NOT NULL,
    brand_color VARCHAR(64) NOT NULL,
    logo_url VARCHAR(2048),
    gemini_job_name VARCHAR(255),
    error_message TEXT,
    pdf_status VARCHAR(32),
    pdf_file_path VARCHAR(1024),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    job_diagnostic_message TEXT,
    job_recommended_actions JSONB,
    gap_batch_idempotency_key UUID,
    create_idempotency_key UUID,
    gap_analysis_gemini_job_name VARCHAR(512),
    gap_analysis_completed BOOLEAN NOT NULL,
    CONSTRAINT fk_jobs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL
);

CREATE TRIGGER trg_jobs_updated_at
    BEFORE UPDATE ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_jobs_applied_plan_immutable
    BEFORE UPDATE ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION jobs_prevent_applied_plan_change();

CREATE UNIQUE INDEX ux_jobs_tenant_create_idempotency_key ON jobs (tenant_id, create_idempotency_key)
    WHERE create_idempotency_key IS NOT NULL;

CREATE TABLE job_queries (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    job_id UUID NOT NULL,
    query_text TEXT NOT NULL,
    processed BOOLEAN NOT NULL,
    CONSTRAINT fk_job_queries_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE TABLE audit_histories (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    job_id UUID NOT NULL,
    project_id UUID NOT NULL,
    query TEXT NOT NULL,
    raw_response JSONB NOT NULL,
    som_score DOUBLE PRECISION NOT NULL,
    brand_mentioned BOOLEAN NOT NULL,
    mention_rank INTEGER,
    overall_score INTEGER,
    resolved_entity_label VARCHAR(512),
    token_count INTEGER NOT NULL,
    rank_position INTEGER NOT NULL,
    sentiment_intensity DOUBLE PRECISION NOT NULL,
    visibility_stage INTEGER,
    calculation_version VARCHAR(32),
    negative_alert BOOLEAN NOT NULL,
    modified_z_score DOUBLE PRECISION,
    diagnostic_message TEXT,
    recommended_actions JSONB,
    model_insights JSONB,
    audit_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_histories_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_histories_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_audit_histories_tenant_project_date ON audit_histories (tenant_id, project_id, audit_date);

CREATE INDEX idx_audit_histories_job_id ON audit_histories (job_id);

CREATE TABLE job_competitor_scores (
    id UUID PRIMARY KEY,
    audit_history_id UUID NOT NULL,
    competitor_name VARCHAR(512) NOT NULL,
    som_score DOUBLE PRECISION NOT NULL,
    rank_position INTEGER,
    visibility_stage INTEGER,
    match_status VARCHAR(32) NOT NULL,
    noun_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_job_competitor_scores_audit FOREIGN KEY (audit_history_id) REFERENCES audit_histories (id) ON DELETE CASCADE
);

CREATE INDEX idx_job_competitor_scores_audit ON job_competitor_scores (audit_history_id);

CREATE TABLE sge_results (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    job_id UUID NOT NULL,
    query_id UUID NOT NULL,
    query TEXT NOT NULL,
    sge_raw_response JSONB NOT NULL,
    sge_mentioned BOOLEAN NOT NULL,
    mention_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sge_results_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE TABLE rag_domain_rules (
    id UUID PRIMARY KEY,
    host_suffix VARCHAR(255) NOT NULL,
    rule_kind VARCHAR(32) NOT NULL,
    trust_boost DOUBLE PRECISION,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_rag_domain_rules_host_suffix UNIQUE (host_suffix)
);

CREATE TABLE unresolved_entity_queue (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    left_label TEXT NOT NULL,
    right_label TEXT NOT NULL,
    left_blocking_hash VARCHAR(64) NOT NULL,
    right_blocking_hash VARCHAR(64) NOT NULL,
    manual_review_required BOOLEAN NOT NULL,
    calculation_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE revinfo (
    rev SERIAL PRIMARY KEY,
    revtstmp BIGINT NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    operator_user_id VARCHAR(320) NOT NULL
);

CREATE TABLE projects_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    name VARCHAR(255),
    target_url VARCHAR(255),
    brand_color VARCHAR(64),
    logo_url VARCHAR(2048),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    auto_audit_enabled BOOLEAN,
    slack_webhook_url VARCHAR(2048),
    notification_email VARCHAR(320),
    last_audit_at TIMESTAMP,
    CONSTRAINT fk_projects_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_projects_aud PRIMARY KEY (rev, id)
);

CREATE TABLE project_competitors_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    project_id UUID,
    competitor_url VARCHAR(2048),
    CONSTRAINT fk_project_competitors_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_project_competitors_aud PRIMARY KEY (rev, project_id, competitor_url)
);

CREATE TABLE jobs_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    project_id UUID,
    job_status VARCHAR(20),
    subscription_plan VARCHAR(16),
    plan_limits_snapshot JSONB,
    brand_name VARCHAR(255),
    brand_color VARCHAR(64),
    logo_url VARCHAR(2048),
    gemini_job_name VARCHAR(255),
    error_message TEXT,
    pdf_status VARCHAR(32),
    pdf_file_path VARCHAR(1024),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    job_diagnostic_message TEXT,
    job_recommended_actions JSONB,
    gap_batch_idempotency_key UUID,
    create_idempotency_key UUID,
    gap_analysis_gemini_job_name VARCHAR(512),
    gap_analysis_completed BOOLEAN,
    CONSTRAINT fk_jobs_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_jobs_aud PRIMARY KEY (rev, id)
);

CREATE TABLE project_keywords_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    project_id UUID,
    keyword_text TEXT,
    analysis_priority VARCHAR(16),
    preferred_engine VARCHAR(32),
    CONSTRAINT fk_project_keywords_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_project_keywords_aud PRIMARY KEY (rev, id)
);

CREATE TABLE job_queries_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    job_id UUID,
    query_text TEXT,
    processed BOOLEAN,
    CONSTRAINT fk_job_queries_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_job_queries_aud PRIMARY KEY (rev, id)
);

CREATE TABLE audit_histories_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    job_id UUID,
    project_id UUID,
    query TEXT,
    raw_response JSONB,
    som_score DOUBLE PRECISION,
    brand_mentioned BOOLEAN,
    mention_rank INTEGER,
    overall_score INTEGER,
    resolved_entity_label VARCHAR(512),
    token_count INTEGER,
    rank_position INTEGER,
    sentiment_intensity DOUBLE PRECISION,
    visibility_stage INTEGER,
    calculation_version VARCHAR(32),
    negative_alert BOOLEAN,
    modified_z_score DOUBLE PRECISION,
    diagnostic_message TEXT,
    recommended_actions JSONB,
    model_insights JSONB,
    audit_date DATE,
    created_at TIMESTAMPTZ,
    CONSTRAINT fk_audit_histories_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_audit_histories_aud PRIMARY KEY (rev, id)
);

CREATE TABLE sge_results_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    job_id UUID,
    query_id UUID,
    query TEXT,
    sge_raw_response JSONB,
    sge_mentioned BOOLEAN,
    mention_count INTEGER,
    created_at TIMESTAMP,
    CONSTRAINT fk_sge_results_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_sge_results_aud PRIMARY KEY (rev, id)
);

CREATE TABLE unresolved_entity_queue_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    tenant_id VARCHAR(36),
    left_label TEXT,
    right_label TEXT,
    left_blocking_hash VARCHAR(64),
    right_blocking_hash VARCHAR(64),
    manual_review_required BOOLEAN,
    calculation_version VARCHAR(32),
    created_at TIMESTAMP,
    CONSTRAINT fk_unresolved_entity_queue_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_unresolved_entity_queue_aud PRIMARY KEY (rev, id)
);

CREATE TABLE workspaces_aud (
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    id UUID,
    name VARCHAR(512),
    subscription_plan VARCHAR(16),
    organization_id UUID,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_workspaces_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT pk_workspaces_aud PRIMARY KEY (rev, id)
);

INSERT INTO plans (id, name, monthly_price, monthly_credits, keyword_limit, created_at, updated_at)
VALUES ('STANDARD', 'Standard', 0, 1000000, 1000, now(), now());

INSERT INTO organizations (id, name, plan_id, credit_balance, billing_cycle_anchor, created_at, updated_at, deleted_at)
VALUES ('00000000-0000-4000-8000-000000000001', 'Default Org', 'STANDARD', 0, now(), now(), now(), NULL);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'api_worker') THEN
        CREATE ROLE api_worker NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'batch_worker') THEN
        CREATE ROLE batch_worker NOLOGIN;
    END IF;
END $$;

ALTER ROLE batch_worker BYPASSRLS;

GRANT USAGE ON SCHEMA public TO api_worker, batch_worker;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.plans TO batch_worker;
GRANT SELECT ON TABLE public.plans TO api_worker;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.organizations TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.organization_users TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.tenants TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.user_sessions TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.workspaces TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.users TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projects TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.project_competitors TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.project_keywords TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobs TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.job_queries TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.audit_histories TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.job_competitor_scores TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.sge_results TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.rag_domain_rules TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.unresolved_entity_queue TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.revinfo TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projects_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.project_competitors_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.jobs_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.project_keywords_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.job_queries_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.audit_histories_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.sge_results_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.unresolved_entity_queue_aud TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.workspaces_aud TO api_worker, batch_worker;

GRANT USAGE, SELECT ON SEQUENCE public.revinfo_rev_seq TO api_worker, batch_worker;

GRANT EXECUTE ON FUNCTION public.update_updated_at_column() TO api_worker, batch_worker;
GRANT EXECUTE ON FUNCTION public.jobs_prevent_applied_plan_change() TO api_worker, batch_worker;

ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organizations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS organizations_api_worker_policy ON public.organizations;
CREATE POLICY organizations_api_worker_policy ON public.organizations
    FOR ALL TO api_worker
    USING (
        id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        AND deleted_at IS NULL
    )
    WITH CHECK (
        id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.organization_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organization_users FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS organization_users_api_worker_policy ON public.organization_users;
CREATE POLICY organization_users_api_worker_policy ON public.organization_users
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        AND deleted_at IS NULL
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tenants FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenants_api_worker_policy ON public.tenants;
CREATE POLICY tenants_api_worker_policy ON public.tenants
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        AND deleted_at IS NULL
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_sessions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_sessions_api_worker_policy ON public.user_sessions;
CREATE POLICY user_sessions_api_worker_policy ON public.user_sessions
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        AND deleted_at IS NULL
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workspaces_api_worker_policy ON public.workspaces;
CREATE POLICY workspaces_api_worker_policy ON public.workspaces
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
        AND deleted_at IS NULL
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS projects_api_worker_policy ON public.projects;
CREATE POLICY projects_api_worker_policy ON public.projects
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = projects.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = projects.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.project_competitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_competitors FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_competitors_api_worker_policy ON public.project_competitors;
CREATE POLICY project_competitors_api_worker_policy ON public.project_competitors
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.projects p JOIN public.workspaces w ON w.id::text = p.tenant_id WHERE p.id = project_competitors.project_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.projects p JOIN public.workspaces w ON w.id::text = p.tenant_id WHERE p.id = project_competitors.project_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.project_keywords ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_keywords FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_keywords_api_worker_policy ON public.project_keywords;
CREATE POLICY project_keywords_api_worker_policy ON public.project_keywords
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = project_keywords.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = project_keywords.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS jobs_api_worker_policy ON public.jobs;
CREATE POLICY jobs_api_worker_policy ON public.jobs
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = jobs.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = jobs.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.job_queries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_queries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS job_queries_api_worker_policy ON public.job_queries;
CREATE POLICY job_queries_api_worker_policy ON public.job_queries
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = job_queries.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = job_queries.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.audit_histories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_histories FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_histories_api_worker_policy ON public.audit_histories;
CREATE POLICY audit_histories_api_worker_policy ON public.audit_histories
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = audit_histories.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = audit_histories.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.job_competitor_scores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_competitor_scores FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS job_competitor_scores_api_worker_policy ON public.job_competitor_scores;
CREATE POLICY job_competitor_scores_api_worker_policy ON public.job_competitor_scores
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.audit_histories ah JOIN public.workspaces w ON w.id::text = ah.tenant_id WHERE ah.id = job_competitor_scores.audit_history_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.audit_histories ah JOIN public.workspaces w ON w.id::text = ah.tenant_id WHERE ah.id = job_competitor_scores.audit_history_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.sge_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sge_results FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sge_results_api_worker_policy ON public.sge_results;
CREATE POLICY sge_results_api_worker_policy ON public.sge_results
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = sge_results.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = sge_results.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.unresolved_entity_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.unresolved_entity_queue FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS unresolved_entity_queue_api_worker_policy ON public.unresolved_entity_queue;
CREATE POLICY unresolved_entity_queue_api_worker_policy ON public.unresolved_entity_queue
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = unresolved_entity_queue.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = unresolved_entity_queue.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.rag_domain_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rag_domain_rules FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rag_domain_rules_api_worker_policy ON public.rag_domain_rules;
CREATE POLICY rag_domain_rules_api_worker_policy ON public.rag_domain_rules
    FOR ALL TO api_worker
    USING (TRUE)
    WITH CHECK (TRUE);

ALTER TABLE public.revinfo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.revinfo FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS revinfo_api_worker_policy ON public.revinfo;
CREATE POLICY revinfo_api_worker_policy ON public.revinfo
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = revinfo.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = revinfo.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.projects_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS projects_aud_api_worker_policy ON public.projects_aud;
CREATE POLICY projects_aud_api_worker_policy ON public.projects_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = projects_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = projects_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.project_competitors_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_competitors_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_competitors_aud_api_worker_policy ON public.project_competitors_aud;
CREATE POLICY project_competitors_aud_api_worker_policy ON public.project_competitors_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.projects p JOIN public.workspaces w ON w.id::text = p.tenant_id WHERE p.id = project_competitors_aud.project_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.projects p JOIN public.workspaces w ON w.id::text = p.tenant_id WHERE p.id = project_competitors_aud.project_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.jobs_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jobs_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS jobs_aud_api_worker_policy ON public.jobs_aud;
CREATE POLICY jobs_aud_api_worker_policy ON public.jobs_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = jobs_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = jobs_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.project_keywords_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_keywords_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS project_keywords_aud_api_worker_policy ON public.project_keywords_aud;
CREATE POLICY project_keywords_aud_api_worker_policy ON public.project_keywords_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = project_keywords_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = project_keywords_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.job_queries_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_queries_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS job_queries_aud_api_worker_policy ON public.job_queries_aud;
CREATE POLICY job_queries_aud_api_worker_policy ON public.job_queries_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = job_queries_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = job_queries_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.audit_histories_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_histories_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_histories_aud_api_worker_policy ON public.audit_histories_aud;
CREATE POLICY audit_histories_aud_api_worker_policy ON public.audit_histories_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = audit_histories_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = audit_histories_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.sge_results_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sge_results_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS sge_results_aud_api_worker_policy ON public.sge_results_aud;
CREATE POLICY sge_results_aud_api_worker_policy ON public.sge_results_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = sge_results_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = sge_results_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.unresolved_entity_queue_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.unresolved_entity_queue_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS unresolved_entity_queue_aud_api_worker_policy ON public.unresolved_entity_queue_aud;
CREATE POLICY unresolved_entity_queue_aud_api_worker_policy ON public.unresolved_entity_queue_aud
    FOR ALL TO api_worker
    USING (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = unresolved_entity_queue_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        (SELECT w.organization_id FROM public.workspaces w WHERE w.id::text = unresolved_entity_queue_aud.tenant_id LIMIT 1)
            = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

ALTER TABLE public.workspaces_aud ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workspaces_aud FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workspaces_aud_api_worker_policy ON public.workspaces_aud;
CREATE POLICY workspaces_aud_api_worker_policy ON public.workspaces_aud
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

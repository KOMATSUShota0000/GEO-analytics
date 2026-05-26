ALTER TABLE public.organizations
    ADD COLUMN IF NOT EXISTS logo_file_path VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS brand_color VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tool_name VARCHAR(255);

CREATE TABLE public.geo_asset_snapshots (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    snapshot_date DATE NOT NULL,
    readiness_score DOUBLE PRECISION NOT NULL,
    local_trust_count BIGINT NOT NULL,
    CONSTRAINT fk_geo_asset_snapshots_organization FOREIGN KEY (organization_id) REFERENCES public.organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_geo_asset_snapshots_project FOREIGN KEY (project_id) REFERENCES public.projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_geo_asset_snapshots_org_snapshot_date ON public.geo_asset_snapshots (organization_id, snapshot_date DESC);
CREATE INDEX idx_geo_asset_snapshots_project_snapshot_date ON public.geo_asset_snapshots (project_id, snapshot_date DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.geo_asset_snapshots TO api_worker, batch_worker;

ALTER TABLE public.geo_asset_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.geo_asset_snapshots FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS geo_asset_snapshots_api_worker_policy ON public.geo_asset_snapshots;
CREATE POLICY geo_asset_snapshots_api_worker_policy ON public.geo_asset_snapshots
    FOR ALL TO api_worker
    USING (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid
    );

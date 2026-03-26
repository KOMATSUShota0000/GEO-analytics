ALTER TABLE projects RENAME COLUMN workspace_id TO tenant_id;
ALTER TABLE projects ALTER COLUMN tenant_id TYPE varchar(36) USING trim(tenant_id::text);
ALTER TABLE project_keywords RENAME COLUMN workspace_id TO tenant_id;
ALTER TABLE project_keywords ALTER COLUMN tenant_id TYPE varchar(36) USING trim(tenant_id::text);
ALTER TABLE jobs RENAME COLUMN workspace_id TO tenant_id;
ALTER TABLE jobs ALTER COLUMN tenant_id TYPE varchar(36) USING trim(tenant_id::text);
UPDATE jobs SET tenant_id = '00000000-0000-0000-0000-000000000000' WHERE tenant_id IS NULL OR tenant_id = '';
ALTER TABLE jobs ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_histories RENAME COLUMN workspace_id TO tenant_id;
ALTER TABLE audit_histories ALTER COLUMN tenant_id TYPE varchar(36) USING trim(tenant_id::text);
ALTER TABLE job_queries ADD COLUMN IF NOT EXISTS tenant_id varchar(36);
UPDATE job_queries q SET tenant_id = trim(j.tenant_id::text) FROM jobs j WHERE q.job_id = j.id AND (q.tenant_id IS NULL OR q.tenant_id = '');
ALTER TABLE job_queries ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sge_results ADD COLUMN IF NOT EXISTS tenant_id varchar(36);
UPDATE sge_results s SET tenant_id = trim(j.tenant_id::text) FROM jobs j WHERE s.job_id = j.id AND (s.tenant_id IS NULL OR s.tenant_id = '');
ALTER TABLE sge_results ALTER COLUMN tenant_id SET NOT NULL;
DROP INDEX IF EXISTS idx_audit_histories_workspace_project_date;
CREATE INDEX IF NOT EXISTS idx_audit_histories_tenant_project_date ON audit_histories (tenant_id, project_id, audit_date);
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON projects;
CREATE POLICY tenant_isolation_policy ON projects
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE project_keywords ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_keywords FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON project_keywords;
CREATE POLICY tenant_isolation_policy ON project_keywords
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON jobs;
CREATE POLICY tenant_isolation_policy ON jobs
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE job_queries ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_queries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON job_queries;
CREATE POLICY tenant_isolation_policy ON job_queries
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE audit_histories ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_histories FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON audit_histories;
CREATE POLICY tenant_isolation_policy ON audit_histories
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE sge_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE sge_results FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON sge_results;
CREATE POLICY tenant_isolation_policy ON sge_results
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON workspaces;
CREATE POLICY tenant_isolation_policy ON workspaces
  USING (id::text = current_setting('app.current_tenant', true))
  WITH CHECK (id::text = current_setting('app.current_tenant', true));
ALTER TABLE project_competitors ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_competitors FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_policy ON project_competitors;
CREATE POLICY tenant_isolation_policy ON project_competitors
  USING (EXISTS (SELECT 1 FROM projects p WHERE p.id = project_competitors.project_id AND p.tenant_id = current_setting('app.current_tenant', true)))
  WITH CHECK (EXISTS (SELECT 1 FROM projects p WHERE p.id = project_competitors.project_id AND p.tenant_id = current_setting('app.current_tenant', true)));

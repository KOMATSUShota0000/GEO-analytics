# public.projects

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid |  | false | [public.project_competitors](public.project_competitors.md) [public.project_keywords](public.project_keywords.md) [public.jobs](public.jobs.md) [public.audit_histories](public.audit_histories.md) [public.wallet_transactions](public.wallet_transactions.md) [public.geo_asset_snapshots](public.geo_asset_snapshots.md) |  |  |
| tenant_id | varchar(36) |  | false |  |  |  |
| name | varchar(255) |  | false |  |  |  |
| target_url | varchar(255) |  | false |  |  |  |
| brand_color | varchar(64) |  | false |  |  |  |
| logo_url | varchar(2048) |  | true |  |  |  |
| created_at | timestamp without time zone |  | false |  |  |  |
| updated_at | timestamp without time zone |  | false |  |  |  |
| auto_audit_enabled | boolean |  | false |  |  |  |
| slack_webhook_url | varchar(2048) |  | true |  |  |  |
| notification_email | varchar(320) |  | true |  |  |  |
| last_audit_at | timestamp without time zone |  | true |  |  |  |
| industry_type | varchar(32) | 'OTHER'::character varying | false |  |  | 業種分類（IndustryTypeの列挙子名。既存行はOTHER） |
| extracted_strengths | text |  | true |  |  | 自社サイト解析等で抽出した強み |
| target_audience | text |  | true |  |  | 想定ターゲット層 |
| minority_reports | jsonb | '[]'::jsonb | false |  |  |  |
| competitor_profiles | jsonb | '[]'::jsonb | false |  |  |  |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| projects_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| projects_pkey | CREATE UNIQUE INDEX projects_pkey ON public.projects USING btree (id) |

## Triggers

| Name | Definition |
| ---- | ---------- |
| trg_projects_updated_at | CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON public.projects FOR EACH ROW EXECUTE FUNCTION update_updated_at_column() |

## Relations

```mermaid
erDiagram

"public.project_competitors" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.project_keywords" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.jobs" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.job_queries" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.audit_histories" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.sge_results" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.audit_histories" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.wallet_transactions" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.wallet_transactions" }o--o| "public.wallet_transactions" : "FOREIGN KEY (parent_reservation_id) REFERENCES wallet_transactions(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.geo_asset_snapshots" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.geo_asset_snapshots" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"

"public.projects" {
  uuid id ""
  varchar_36_ tenant_id ""
  varchar_255_ name ""
  varchar_255_ target_url ""
  varchar_64_ brand_color ""
  varchar_2048_ logo_url ""
  timestamp_without_time_zone created_at ""
  timestamp_without_time_zone updated_at ""
  boolean auto_audit_enabled ""
  varchar_2048_ slack_webhook_url ""
  varchar_320_ notification_email ""
  timestamp_without_time_zone last_audit_at ""
  varchar_32_ industry_type "業種分類（IndustryTypeの列挙子名。既存行はOTHER）"
  text extracted_strengths "自社サイト解析等で抽出した強み"
  text target_audience "想定ターゲット層"
  jsonb minority_reports ""
  jsonb competitor_profiles ""
}
"public.project_competitors" {
  uuid project_id FK ""
  varchar_2048_ competitor_url ""
}
"public.project_keywords" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid project_id FK ""
  text keyword_text ""
  varchar_16_ analysis_priority ""
  varchar_32_ preferred_engine ""
}
"public.jobs" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid project_id FK ""
  varchar_32_ job_status ""
  varchar_16_ subscription_plan ""
  jsonb plan_limits_snapshot ""
  varchar_255_ brand_name ""
  varchar_64_ brand_color ""
  varchar_2048_ logo_url ""
  varchar_255_ gemini_job_name ""
  text error_message ""
  varchar_32_ pdf_status ""
  varchar_1024_ pdf_file_path ""
  timestamp_without_time_zone created_at ""
  timestamp_without_time_zone updated_at ""
  text job_diagnostic_message ""
  jsonb job_recommended_actions ""
  uuid gap_batch_idempotency_key ""
  uuid create_idempotency_key ""
  varchar_512_ gap_analysis_gemini_job_name ""
  boolean gap_analysis_completed ""
  jsonb self_rubric_audit_json ""
  jsonb competitor_rubric_audits_json ""
  jsonb self_crawled_page_json ""
  integer meo_review_count ""
  double_precision meo_average_stars ""
  jsonb emotional_alert ""
  varchar_2048_ target_url ""
  text business_summary ""
  text target_audience ""
  text focus_points ""
  text extracted_knowledge ""
  varchar_32_ industry_type ""
  varchar_32_ job_advice_source "ジョブ全体アドバイスの生成元（AdviceSource: AI / TEMPLATE_FALLBACK）"
}
"public.job_queries" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid job_id FK ""
  text query_text ""
  boolean processed ""
}
"public.audit_histories" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid job_id FK ""
  uuid project_id FK ""
  text query ""
  jsonb raw_response ""
  double_precision som_score ""
  boolean brand_mentioned ""
  integer mention_rank ""
  integer overall_score ""
  varchar_512_ resolved_entity_label ""
  integer token_count ""
  integer ai_citation_position ""
  double_precision sentiment_intensity ""
  integer visibility_stage ""
  varchar_32_ calculation_version ""
  boolean negative_alert ""
  double_precision modified_z_score ""
  text diagnostic_message ""
  jsonb recommended_actions ""
  jsonb model_insights ""
  date audit_date ""
  timestamp_with_time_zone created_at ""
  numeric gbvs_normalized_score ""
  jsonb job_recommended_actions ""
  varchar_32_ ai_recognition_state ""
}
"public.sge_results" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid job_id FK ""
  uuid query_id ""
  text query ""
  jsonb sge_raw_response ""
  boolean sge_mentioned ""
  integer mention_count ""
  timestamp_without_time_zone created_at ""
}
"public.wallet_transactions" {
  uuid id ""
  uuid organization_id FK ""
  uuid project_id FK ""
  varchar_32_ transaction_type ""
  bigint amount ""
  uuid parent_reservation_id FK ""
  timestamp_without_time_zone created_at ""
  varchar_2048_ note ""
}
"public.organizations" {
  uuid id ""
  varchar_512_ name ""
  varchar_20_ plan_id FK ""
  bigint credit_balance ""
  timestamp_with_time_zone billing_cycle_anchor ""
  timestamp_with_time_zone created_at ""
  timestamp_with_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
  varchar_1024_ logo_file_path ""
  varchar_64_ brand_color ""
  varchar_255_ tool_name ""
}
"public.geo_asset_snapshots" {
  uuid id ""
  uuid organization_id FK ""
  uuid project_id FK ""
  date snapshot_date ""
  double_precision readiness_score ""
  bigint local_trust_count ""
  varchar_32_ calculation_version ""
}
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)

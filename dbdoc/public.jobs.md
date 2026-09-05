# public.jobs

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid |  | false | [public.job_queries](public.job_queries.md) [public.audit_histories](public.audit_histories.md) [public.sge_results](public.sge_results.md) |  |  |
| tenant_id | varchar(36) |  | false |  |  |  |
| project_id | uuid |  | true |  | [public.projects](public.projects.md) |  |
| job_status | varchar(32) |  | false |  |  |  |
| subscription_plan | varchar(16) |  | true |  |  |  |
| plan_limits_snapshot | jsonb |  | true |  |  |  |
| brand_name | varchar(255) |  | false |  |  |  |
| brand_color | varchar(64) |  | false |  |  |  |
| logo_url | varchar(2048) |  | true |  |  |  |
| gemini_job_name | varchar(255) |  | true |  |  |  |
| error_message | text |  | true |  |  |  |
| pdf_status | varchar(32) |  | true |  |  |  |
| pdf_file_path | varchar(1024) |  | true |  |  |  |
| created_at | timestamp without time zone |  | false |  |  |  |
| updated_at | timestamp without time zone |  | false |  |  |  |
| job_diagnostic_message | text |  | true |  |  |  |
| job_recommended_actions | jsonb |  | true |  |  |  |
| gap_batch_idempotency_key | uuid |  | true |  |  |  |
| create_idempotency_key | uuid |  | true |  |  |  |
| gap_analysis_gemini_job_name | varchar(512) |  | true |  |  |  |
| gap_analysis_completed | boolean |  | false |  |  |  |
| self_rubric_audit_json | jsonb |  | true |  |  |  |
| competitor_rubric_audits_json | jsonb |  | true |  |  |  |
| self_crawled_page_json | jsonb |  | true |  |  |  |
| meo_review_count | integer |  | true |  |  |  |
| meo_average_stars | double precision |  | true |  |  |  |
| emotional_alert | jsonb |  | true |  |  |  |
| target_url | varchar(2048) |  | false |  |  |  |
| business_summary | text |  | true |  |  |  |
| target_audience | text |  | true |  |  |  |
| focus_points | text |  | true |  |  |  |
| extracted_knowledge | text |  | true |  |  |  |
| industry_type | varchar(32) | 'LOCAL_STORE'::character varying | false |  |  |  |
| job_advice_source | varchar(32) |  | true |  |  | ジョブ全体アドバイスの生成元（AdviceSource: AI / TEMPLATE_FALLBACK） |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| chk_jobs_industry_type | CHECK | CHECK (((industry_type)::text = ANY ((ARRAY['LOCAL_STORE'::character varying, 'CORPORATE_SERVICE'::character varying, 'ONLINE_SERVICE'::character varying])::text[]))) |
| chk_jobs_job_status | CHECK | CHECK (((job_status)::text = ANY ((ARRAY['CREATED'::character varying, 'EXTRACTING_COMPETITORS'::character varying, 'REALTIME_PROCESSING'::character varying, 'FILE_UPLOADED'::character varying, 'SUBMITTED'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))) |
| chk_jobs_subscription_plan | CHECK | CHECK (((subscription_plan)::text = ANY ((ARRAY['STANDARD'::character varying, 'PRO'::character varying, 'EXPERT'::character varying])::text[]))) |
| fk_jobs_project | FOREIGN KEY | FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL |
| jobs_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| jobs_pkey | CREATE UNIQUE INDEX jobs_pkey ON public.jobs USING btree (id) |
| ux_jobs_tenant_create_idempotency_key | CREATE UNIQUE INDEX ux_jobs_tenant_create_idempotency_key ON public.jobs USING btree (tenant_id, create_idempotency_key) WHERE (create_idempotency_key IS NOT NULL) |

## Triggers

| Name | Definition |
| ---- | ---------- |
| trg_jobs_updated_at | CREATE TRIGGER trg_jobs_updated_at BEFORE UPDATE ON public.jobs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column() |
| trg_jobs_applied_plan_immutable | CREATE TRIGGER trg_jobs_applied_plan_immutable BEFORE UPDATE ON public.jobs FOR EACH ROW EXECUTE FUNCTION jobs_prevent_applied_plan_change() |

## Relations

```mermaid
erDiagram

"public.job_queries" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.audit_histories" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.audit_histories" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.sge_results" }o--|| "public.jobs" : "FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE"
"public.jobs" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.project_keywords" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.wallet_transactions" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.geo_asset_snapshots" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"

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
"public.project_keywords" {
  uuid id ""
  varchar_36_ tenant_id ""
  uuid project_id FK ""
  text keyword_text ""
  varchar_16_ analysis_priority ""
  varchar_32_ preferred_engine ""
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

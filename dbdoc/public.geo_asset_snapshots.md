# public.geo_asset_snapshots

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid |  | false |  |  |  |
| organization_id | uuid |  | false |  | [public.organizations](public.organizations.md) |  |
| project_id | uuid |  | false |  | [public.projects](public.projects.md) |  |
| snapshot_date | date |  | false |  |  |  |
| readiness_score | double precision |  | false |  |  |  |
| local_trust_count | bigint |  | false |  |  |  |
| calculation_version | varchar(32) |  | true |  |  |  |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| fk_geo_asset_snapshots_organization | FOREIGN KEY | FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT |
| fk_geo_asset_snapshots_project | FOREIGN KEY | FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE |
| geo_asset_snapshots_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| geo_asset_snapshots_pkey | CREATE UNIQUE INDEX geo_asset_snapshots_pkey ON public.geo_asset_snapshots USING btree (id) |
| idx_geo_asset_snapshots_org_snapshot_date | CREATE INDEX idx_geo_asset_snapshots_org_snapshot_date ON public.geo_asset_snapshots USING btree (organization_id, snapshot_date DESC) |
| idx_geo_asset_snapshots_project_snapshot_date | CREATE INDEX idx_geo_asset_snapshots_project_snapshot_date ON public.geo_asset_snapshots USING btree (project_id, snapshot_date DESC) |

## Relations

```mermaid
erDiagram

"public.geo_asset_snapshots" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.organization_users" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.tenants" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.user_sessions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.workspaces" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.processed_stripe_events" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE"
"public.organizations" }o--|| "public.plans" : "FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT"
"public.geo_asset_snapshots" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.project_keywords" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.jobs" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.audit_histories" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.wallet_transactions" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"

"public.geo_asset_snapshots" {
  uuid id ""
  uuid organization_id FK ""
  uuid project_id FK ""
  date snapshot_date ""
  double_precision readiness_score ""
  bigint local_trust_count ""
  varchar_32_ calculation_version ""
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
"public.organization_users" {
  uuid id ""
  uuid organization_id FK ""
  varchar_320_ email ""
  varchar_255_ password_hash ""
  varchar_32_ role ""
  timestamp_with_time_zone created_at ""
  timestamp_with_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
}
"public.tenants" {
  uuid id ""
  uuid organization_id FK ""
  varchar_512_ name ""
  timestamp_with_time_zone created_at ""
  timestamp_with_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
}
"public.user_sessions" {
  uuid id ""
  uuid organization_id FK ""
  uuid user_id FK ""
  uuid session_id ""
  timestamp_with_time_zone expires_at ""
  timestamp_with_time_zone created_at ""
  timestamp_with_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
}
"public.workspaces" {
  uuid id ""
  varchar_512_ name ""
  varchar_16_ subscription_plan ""
  uuid organization_id FK ""
  timestamp_without_time_zone created_at ""
  timestamp_without_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
  varchar_64_ stripe_customer_id ""
  varchar_64_ stripe_subscription_id ""
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
"public.processed_stripe_events" {
  uuid id ""
  uuid organization_id FK ""
  varchar_64_ event_id ""
  varchar_64_ event_type ""
  timestamp_without_time_zone processed_at ""
}
"public.plans" {
  varchar_20_ id ""
  varchar_512_ name ""
  bigint monthly_price ""
  bigint monthly_credits ""
  integer keyword_limit ""
  timestamp_with_time_zone created_at ""
  timestamp_with_time_zone updated_at ""
  timestamp_with_time_zone deleted_at ""
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
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)

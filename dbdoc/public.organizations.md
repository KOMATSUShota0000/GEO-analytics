# public.organizations

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid | gen_random_uuid() | false | [public.organization_users](public.organization_users.md) [public.tenants](public.tenants.md) [public.user_sessions](public.user_sessions.md) [public.workspaces](public.workspaces.md) [public.wallet_transactions](public.wallet_transactions.md) [public.geo_asset_snapshots](public.geo_asset_snapshots.md) [public.processed_stripe_events](public.processed_stripe_events.md) |  |  |
| name | varchar(512) |  | false |  |  |  |
| plan_id | varchar(20) |  | false |  | [public.plans](public.plans.md) |  |
| credit_balance | bigint | 0 | false |  |  |  |
| billing_cycle_anchor | timestamp with time zone | now() | false |  |  |  |
| created_at | timestamp with time zone | now() | false |  |  |  |
| updated_at | timestamp with time zone | now() | false |  |  |  |
| deleted_at | timestamp with time zone |  | true |  |  |  |
| logo_file_path | varchar(1024) |  | true |  |  |  |
| brand_color | varchar(64) |  | true |  |  |  |
| tool_name | varchar(255) |  | true |  |  |  |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| fk_organizations_plan | FOREIGN KEY | FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT |
| organizations_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| organizations_pkey | CREATE UNIQUE INDEX organizations_pkey ON public.organizations USING btree (id) |

## Triggers

| Name | Definition |
| ---- | ---------- |
| trg_organizations_updated_at | CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON public.organizations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column() |

## Relations

```mermaid
erDiagram

"public.organization_users" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.user_sessions" }o--|| "public.organization_users" : "FOREIGN KEY (user_id) REFERENCES organization_users(id) ON DELETE RESTRICT"
"public.tenants" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.user_sessions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.workspaces" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--o| "public.wallet_transactions" : "FOREIGN KEY (parent_reservation_id) REFERENCES wallet_transactions(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--o| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL"
"public.geo_asset_snapshots" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.geo_asset_snapshots" }o--|| "public.projects" : "FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE"
"public.processed_stripe_events" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE"
"public.organizations" }o--|| "public.plans" : "FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT"

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
"public.tenants" {
  uuid id ""
  uuid organization_id FK ""
  varchar_512_ name ""
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
"public.geo_asset_snapshots" {
  uuid id ""
  uuid organization_id FK ""
  uuid project_id FK ""
  date snapshot_date ""
  double_precision readiness_score ""
  bigint local_trust_count ""
  varchar_32_ calculation_version ""
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
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)

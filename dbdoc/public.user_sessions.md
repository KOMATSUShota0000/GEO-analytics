# public.user_sessions

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid | gen_random_uuid() | false |  |  |  |
| organization_id | uuid |  | false |  | [public.organizations](public.organizations.md) |  |
| user_id | uuid |  | false |  | [public.organization_users](public.organization_users.md) |  |
| session_id | uuid |  | false |  |  |  |
| expires_at | timestamp with time zone |  | false |  |  |  |
| created_at | timestamp with time zone | now() | false |  |  |  |
| updated_at | timestamp with time zone | now() | false |  |  |  |
| deleted_at | timestamp with time zone |  | true |  |  |  |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| fk_user_sessions_organization | FOREIGN KEY | FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT |
| fk_user_sessions_user | FOREIGN KEY | FOREIGN KEY (user_id) REFERENCES organization_users(id) ON DELETE RESTRICT |
| user_sessions_pkey | PRIMARY KEY | PRIMARY KEY (id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| user_sessions_pkey | CREATE UNIQUE INDEX user_sessions_pkey ON public.user_sessions USING btree (id) |
| uq_user_sessions_session_id_active | CREATE UNIQUE INDEX uq_user_sessions_session_id_active ON public.user_sessions USING btree (session_id) WHERE (deleted_at IS NULL) |
| idx_user_sessions_user_active | CREATE INDEX idx_user_sessions_user_active ON public.user_sessions USING btree (user_id) WHERE (deleted_at IS NULL) |
| idx_user_sessions_expires_at_active | CREATE INDEX idx_user_sessions_expires_at_active ON public.user_sessions USING btree (expires_at) WHERE (deleted_at IS NULL) |
| idx_user_sessions_organization_active | CREATE INDEX idx_user_sessions_organization_active ON public.user_sessions USING btree (organization_id) WHERE (deleted_at IS NULL) |

## Triggers

| Name | Definition |
| ---- | ---------- |
| trg_user_sessions_updated_at | CREATE TRIGGER trg_user_sessions_updated_at BEFORE UPDATE ON public.user_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column() |

## Relations

```mermaid
erDiagram

"public.user_sessions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.organization_users" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.tenants" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.workspaces" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.geo_asset_snapshots" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.processed_stripe_events" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE"
"public.organizations" }o--|| "public.plans" : "FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT"
"public.user_sessions" }o--|| "public.organization_users" : "FOREIGN KEY (user_id) REFERENCES organization_users(id) ON DELETE RESTRICT"

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

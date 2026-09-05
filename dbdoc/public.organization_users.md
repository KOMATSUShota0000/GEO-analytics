# public.organization_users

## Columns

| Name | Type | Default | Nullable | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | -------- | ------- | ------- |
| id | uuid | gen_random_uuid() | false | [public.user_sessions](public.user_sessions.md) |  |  |
| organization_id | uuid |  | false |  | [public.organizations](public.organizations.md) |  |
| email | varchar(320) |  | false |  |  |  |
| password_hash | varchar(255) |  | false |  |  |  |
| role | varchar(32) | 'MEMBER'::character varying | false |  |  |  |
| created_at | timestamp with time zone | now() | false |  |  |  |
| updated_at | timestamp with time zone | now() | false |  |  |  |
| deleted_at | timestamp with time zone |  | true |  |  |  |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| chk_organization_users_role | CHECK | CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'MEMBER'::character varying, 'VIEWER'::character varying])::text[]))) |
| fk_organization_users_organization | FOREIGN KEY | FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT |
| organization_users_pkey | PRIMARY KEY | PRIMARY KEY (id) |
| uq_organization_users_email | UNIQUE | UNIQUE (email) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| organization_users_pkey | CREATE UNIQUE INDEX organization_users_pkey ON public.organization_users USING btree (id) |
| uq_organization_users_email | CREATE UNIQUE INDEX uq_organization_users_email ON public.organization_users USING btree (email) |
| idx_organization_users_org_active | CREATE INDEX idx_organization_users_org_active ON public.organization_users USING btree (organization_id) WHERE (deleted_at IS NULL) |

## Triggers

| Name | Definition |
| ---- | ---------- |
| trg_organization_users_updated_at | CREATE TRIGGER trg_organization_users_updated_at BEFORE UPDATE ON public.organization_users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column() |

## Relations

```mermaid
erDiagram

"public.user_sessions" }o--|| "public.organization_users" : "FOREIGN KEY (user_id) REFERENCES organization_users(id) ON DELETE RESTRICT"
"public.user_sessions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.organization_users" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.tenants" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.workspaces" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.wallet_transactions" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.geo_asset_snapshots" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT"
"public.processed_stripe_events" }o--|| "public.organizations" : "FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE"
"public.organizations" }o--|| "public.plans" : "FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT"

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

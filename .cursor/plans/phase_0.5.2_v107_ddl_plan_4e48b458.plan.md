---
name: Phase 0.5.2 V107 DDL Plan
overview: V1〜V105 の Flyway に `ai_citation_position`（旧 rank_position）をキーにしたインデックスは存在しないため DROP は不要。`audit_histories` と `job_competitor_scores` に「NULL または整数 1 以上」の CHECK のみを追加する DDL 専用 V107 を定義する。`audit_histories_aud` は対象外。
todos:
  - id: add-v107-file
    content: V107__apply_geo_constraints.sql を新規追加（上記 DDL のみ）
    status: pending
  - id: verify-no-zero-writes
    content: アプリ/JDBC で ai_citation_position=0 を書く経路が無いことを grep・結合試験で確認
    status: pending
isProject: false
---

# Phase 0.5.2 - DDL Schema Enforcement（V107 計画）

## 1. インデックス監査結果（V1〜V105）

[geo-analytics/src/main/resources/db/migration/V1__init_schema.sql](geo-analytics/src/main/resources/db/migration/V1__init_schema.sql) および後続 `V*.sql` を `CREATE INDEX` / `rank_position` / `ai_citation_position` で横断検索した結果、**`audit_histories` / `job_competitor_scores` / `audit_histories_aud` に対し、旧 `rank_position` または現 `ai_citation_position` を索引キーに含むインデックスは定義されていない**。

該当テーブルに存在するインデックス（V1 のみ）は次の通りで、いずれも **引用位置列と無関係**。

| インデックス名 | テーブル | 列 |
|----------------|----------|-----|
| `idx_audit_histories_tenant_project_date` | `audit_histories` | `tenant_id`, `project_id`, `audit_date` |
| `idx_audit_histories_job_id` | `audit_histories` | `job_id` |
| `idx_job_competitor_scores_audit` | `job_competitor_scores` | `audit_history_id` |

[tenant_isolation.sql](geo-analytics/src/main/resources/db/rls/tenant_isolation.sql) の `idx_audit_histories_tenant_project_date` は V1 と同名・同定義の運用パッチであり、Flyway チェーン上の追加インデックスでも **`ai_citation_position` 非含有**。

### DROP 対象インデックス名

**なし**（V107 に `DROP INDEX` は不要）。

---

## 2. CHECK 制約の設計

- **意味**: 未言及は `NULL`（V106・V11 定義と一致）。値がある場合は **1 以上の整数**（0 ・負数は不可）。
- **式（PostgreSQL）**: `ai_citation_position IS NULL OR ai_citation_position >= 1`
- **対象**: `audit_histories`, `job_competitor_scores` のみ。  
  **`audit_histories_aud` は Envers 履歴のため CHECK を付けない**（要件どおり）。

**V106 後のデータ**: いずれも `NULL` のため、制約追加は **即時成功**する想定。

**制約名**: テーブル単位で一意な識別子（長すぎない英字スネークケース）。

---

## 3. 作成予定 `V107__apply_geo_constraints.sql` の完全 SQL（DDL のみ）

ファイルパス（計画上）: [geo-analytics/src/main/resources/db/migration/V107__apply_geo_constraints.sql](geo-analytics/src/main/resources/db/migration/V107__apply_geo_constraints.sql)

```sql
ALTER TABLE audit_histories
    ADD CONSTRAINT chk_audit_histories_ai_citation_position_geo
    CHECK (ai_citation_position IS NULL OR ai_citation_position >= 1);

ALTER TABLE job_competitor_scores
    ADD CONSTRAINT chk_job_competitor_scores_ai_citation_position_geo
    CHECK (ai_citation_position IS NULL OR ai_citation_position >= 1);
```

- **含めないもの**: `UPDATE` / `INSERT` 等の DML、既存マイグレーションの編集、RLS/ポリシー変更。
- **アプリ整合**: JPA 側は `Integer` + `null` 伝搬済み。アプリが `0` を書く経路が残っていないことをデプロイ前に grep 推奨（CHECK により DB 層で拒否される）。

---

## 4. 実装後の検証メモ（参考）

```sql
-- 制約の存在確認（例）
SELECT conname FROM pg_constraint
WHERE conrelid = 'audit_histories'::regclass AND conname LIKE 'chk_%ai_citation%';
```

負の値・0 の挿入テストで拒否されることを確認すると安心。

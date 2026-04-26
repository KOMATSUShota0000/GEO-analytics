---
name: Phase 0.5.1 V106 DML Plan
overview: "`ai_citation_position` を持つ3テーブルを NULL で一括リセットし、`preferred_engine` は **現行アプリ Enum（AI_OVERVIEW のみ）と整合する値** で `project_keywords` / `project_keywords_aud` を更新する DML 専用 V106 の草案。要件文書の `AI_OVERVIEW_LIVE` は JPA デシリアライズ破綻のため採用しないか、Enum 追加後に差し替える前提で記載する。"
todos:
  - id: confirm-engine-value
    content: "ステークホルダー確認: preferred_engine を AI_OVERVIEW で統一か、Enum 拡張の上で AI_OVERVIEW_LIVE か"
    status: pending
  - id: add-v106-file
    content: 合意値で V106__clean_slate_data.sql を新規追加（DML のみ）
    status: pending
  - id: staging-verify
    content: ステージングで行数・ロック・Envers 更新結果をスポット検証
    status: pending
isProject: false
---

# Phase 0.5.1 - DML Data Cleansing（V106 SQL 計画）

## 1. 監査結果: `ai_citation_position` を持つテーブル

Flyway 履歴（[V105](geo-analytics/src/main/resources/db/migration/V105__rename_rank_position_to_ai_citation_position.sql)）および [V1__init_schema.sql](geo-analytics/src/main/resources/db/migration/V1__init_schema.sql) に基づき、本リポジトリの SoT 上 **`ai_citation_position` 列が存在するのは次の3つのみ**。

| テーブル | 備考 |
|----------|------|
| `audit_histories` | 本番監査行。V104 で nullable、V105 で物理リネーム済み。 |
| `job_competitor_scores` | 競合スコア行。V105 で物理リネーム済み。 |
| `audit_histories_aud` | Hibernate Envers 監査。V105 で物理リネーム済み。 |

**存在しないもの**: `job_competitor_scores_aud` 等の `*_aud` はスキーマ上未定義（V1 に無し、後続マイグレーションにも無し）。

---

## 2. 監査結果: `preferred_engine` を持つテーブル

| テーブル | 出典 |
|----------|------|
| `project_keywords` | V1: `preferred_engine VARCHAR(32) NOT NULL` |
| `project_keywords_aud` | V1: `preferred_engine VARCHAR(32)`（NULL 可） |

[V103](geo-analytics/src/main/resources/db/migration/V103__rename_preferred_engine_to_geo.sql) では既に `SERP_API` / `GEMINI_BATCH` を **`AI_OVERVIEW`** へ寄せている。

現行 Java: [`PreferredEngine.java`](geo-analytics/src/main/java/com/geo/analytics/domain/enums/PreferredEngine.java) は **`AI_OVERVIEW` のみ**。

---

## 3. 要件 `AI_OVERVIEW_LIVE` について（実装前に要判断）

タスク記載の **`'AI_OVERVIEW_LIVE'`** は、**現行 `PreferredEngine` に定数が存在しない**ため、そのまま UPDATE すると JPA の `@Enumerated(STRING)` 読み込みで **実行時例外**のリスクが高い。

**推奨（本計画の既定）**: V106 の DML では **`'AI_OVERVIEW'`** を使用し、V103・Enum と三者一致させる。

**LIVE を本当に導入する場合の前提**: 先に `PreferredEngine` に `AI_OVERVIEW_LIVE`（または名称整理）を追加し、既存データとアプリを揃えた **後** に同値で UPDATE する（本 Phase 0.5.1 のスコープ外の設計変更）。

以下の「完全な SQL」は **安全側の `'AI_OVERVIEW'`** で記載する。

---

## 4. V106 の設計方針（制約遵守）

- **過去 Flyway（V103 等）の編集禁止**（チェックサム破壊防止）。
- **V106 は DML のみ**（`ALTER` / `DROP` / `CREATE` / `TRUNCATE` 等の DDL は含めない）。
- `ai_citation_position`: **0 ではなく `NULL`** で統一（未言及・リセット後の意味を V11 定義に合わせる）。
- `preferred_engine`: 上記2テーブルを **単一値に正規化**（クリーンスレート）。

**任意の運用注意**: `audit_histories_aud` は履歴が膨らむため、本 UPDATE は行数・ロック時間の観点でメンテナンスウィンドウを推奨（計画上のメモに留める）。

---

## 5. 作成予定ファイルの完全 SQL（DML のみ）

ファイル名: [geo-analytics/src/main/resources/db/migration/V106__clean_slate_data.sql](geo-analytics/src/main/resources/db/migration/V106__clean_slate_data.sql)（**未作成・計画のみ**）

```sql
UPDATE audit_histories SET ai_citation_position = NULL;
UPDATE job_competitor_scores SET ai_citation_position = NULL;
UPDATE audit_histories_aud SET ai_citation_position = NULL;

UPDATE project_keywords SET preferred_engine = 'AI_OVERVIEW';
UPDATE project_keywords_aud SET preferred_engine = 'AI_OVERVIEW';
```

**要件どおり `AI_OVERVIEW_LIVE` にしたい場合**は、上記2行の右辺を `'AI_OVERVIEW_LIVE'` に置換する **前に** `PreferredEngine` とアプリ全体の整合を取ること（本計画では非推奨）。

---

## 6. 実装後の検証メモ（参考）

- `SELECT COUNT(*) FROM audit_histories WHERE ai_citation_position IS NOT NULL;` → 0 期待。
- `SELECT DISTINCT preferred_engine FROM project_keywords;` → `AI_OVERVIEW` のみ期待。
- アプリ起動後、キーワード一覧・ジョブ参照で Enum デシリアライズエラーが無いこと。

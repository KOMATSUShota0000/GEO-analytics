---
name: Phase 2.2 Batch1 Backend Plan
overview: ジョブ作成DTOには競合フィールドが存在しない一方、`JobProjectResponse` と `project_competitors` が競合URLの永続化・API露出に使われている。Step 1 では入力経路の棚卸し、`JobStatus` と DB の VARCHAR 長拡張、および読取レスポンスの方針整理が中心になる。
todos:
  - id: flyway-job-status-length
    content: "Flyway: jobs / jobs_aud の job_status を VARCHAR(32) へ拡張"
    status: pending
  - id: enum-job-status
    content: JobStatus に EXTRACTING_COMPETITORS 追加、JobEntity の length 整合
    status: pending
  - id: sse-extracting-status
    content: JobController.streamJob で新ステータスの SSE 挙動を決め実装（任意・キュー連携と同期）
    status: pending
  - id: dto-competitor-read-policy
    content: JobProjectResponse の competitor_urls を読取維持か削除かプロダクト決定し実装
    status: pending
  - id: frontend-status-list
    content: フロント processing リストへ新ステータス追加（別コミット可）
    status: pending
isProject: false
---

# 第1回：バックエンド API 契約と DB スキーマ改修（調査ベース計画）

## 調査サマリー（現状）

- **ジョブ作成 DTO**: [`CreateJobRequest.java`](geo-analytics/src/main/java/com/geo/analytics/web/dto/CreateJobRequest.java) は **`brandName`** と **`idempotency_key` のみ**。競合 URL を受け取るフィールドは **ない**。
- **ジョブ作成 Controller**: [`JobController#createJob`](geo-analytics/src/main/java/com/geo/analytics/web/controller/JobController.java) は上記 DTO のみ使用。[`JobPersistenceService#createJobWithIdempotency`](geo-analytics/src/main/java/com/geo/analytics/application/service/JobPersistenceService.java) にブランド名だけ渡す。
- **競合の保存場所**: [`ProjectEntity`](geo-analytics/src/main/java/com/geo/analytics/domain/entity/ProjectEntity.java) の `@ElementCollection` → テーブル **`project_competitors`**（[`V1__init_schema.sql`](geo-analytics/src/main/resources/db/migration/V1__init_schema.sql)）。**`JobEntity` には競合カラムなし**。
- **競合の API 露出（読取）**: [`JobProjectResponse`](geo-analytics/src/main/java/com/geo/analytics/web/dto/JobProjectResponse.java) が `competitorUrls` を [`JobAnalysisDetailResponse`](geo-analytics/src/main/java/com/geo/analytics/web/dto/JobAnalysisDetailResponse.java) 経由で返却。プロジェクト設定 PATCH は [`ProjectSettingsPatchRequest`](geo-analytics/src/main/java/com/geo/analytics/web/dto/ProjectSettingsPatchRequest.java) に競合項目なし（[`ProjectSettingsService`](geo-analytics/src/main/java/com/geo/analytics/application/service/ProjectSettingsService.java) でも未操作）。
- **ジョブステータス**: [`JobStatus.java`](geo-analytics/src/main/java/com/geo/analytics/domain/enums/JobStatus.java) は `CREATED` … `FAILED`。DB は **`jobs.job_status VARCHAR(20)`** および **`jobs_aud.job_status VARCHAR(20)`**（同一マイグレーション）。提案の **`EXTRACTING_COMPETITORS` は 23 文字**のため **現状スキーマでは格納不可**（`REALTIME_PROCESSING` は 19 文字でギリ収まり）。
- **テスト**: `CreateJobRequest` / `JobProjectResponse` を直接検証する専用テストは見当たらない。[`SubscriptionIntegrationTest`](geo-analytics/src/test/java/com/geo/analytics/integration/SubscriptionIntegrationTest.java) は `job_competitor_scores` と検証結果の competitor と関連（**手動競合 URL の入力とは別レイヤ**）。

---

## 1. API 契約（DTO）とサービス層の改修方針

| ファイル | 変更方針 |
|---------|----------|
| [`CreateJobRequest.java`](geo-analytics/src/main/java/com/geo/analytics/web/dto/CreateJobRequest.java) | **削除対象フィールドなし**。ドキュメントまたはチーム内共有として「競合は受け付けない」を明文化する程度（コード変更は任意）。 |
| [`JobController.java`](geo-analytics/src/main/java/com/geo/analytics/web/controller/JobController.java) | 競合関連のシグネチャ変更は **不要**（現状なし）。新ステータス追加後、[`streamJob`](geo-analytics/src/main/java/com/geo/analytics/web/controller/JobController.java) の分岐で **`EXTRACTING_COMPETITORS` を「処理中」として扱うか／SSE をどうするか** を別タスクで決め、必要なら **`register` vs ephemeral** の条件を拡張する（実装はキュー連携 Step とセットが自然）。 |
| [`JobProjectResponse.java`](geo-analytics/src/main/java/com/geo/analytics/web/dto/JobProjectResponse.java) | **判断が必要**: 「ユーザー入力の廃止」のみなら、**読取の `competitor_urls` はフェーズ2.2の抽出結果を載せるため維持**するのが一貫（DB は `project_competitors` のまま）。**レスポンスからも完全に消す**場合は、抽出完了まで空配列または別 DTO に移行する方針とフロント破壊をセットで整理する。Step 1 の推奨は **維持（読取専用・サーバが埋める）**。 |
| [`JobPersistenceService`](geo-analytics/src/main/java/com/geo/analytics/application/service/JobPersistenceService.java) / [`ProjectManagementService`](geo-analytics/src/main/java/com/geo/analytics/application/service/ProjectManagementService.java) | 新規プロジェクト作成時の **`setCompetitorUrls(empty)`** はそのままでよい（抽出前は空）。競合を書き込むのは今後のハイブリッドエンジン経由に寄せる。 |
| [`GeminiResultProcessor`](geo-analytics/src/main/java/com/geo/analytics/application/service/GeminiResultProcessor.java) / [`JobQuerySubmissionService`](geo-analytics/src/main/java/com/geo/analytics/application/service/JobQuerySubmissionService.java) / [`BatchPersistenceService`](geo-analytics/src/main/java/com/geo/analytics/application/service/BatchPersistenceService.java) | **`loadCompetitorHosts` / `findCompetitorUrlsByProjectId`** は、抽出後に `project_competitors` が埋まる前提で **残す**（削除しない）。 |

---

## 2. エンティティと DB スキーマの改修方針

| 項目 | 方針 |
|------|------|
| [`JobStatus.java`](geo-analytics/src/main/java/com/geo/analytics/domain/enums/JobStatus.java) | **`EXTRACTING_COMPETITORS`**（文言どおりの場合）または **`COMPETITOR_EXTRACTION`** のような **20 文字以内の別名**で VARCHAR(20) を維持するか、列長拡張とセットで追加するかを決める。**推奨: 列を `VARCHAR(32)` に広げ、`EXTRACTING_COMPETITORS` を追加**（将来のステータス名にも余裕）。 |
| [`JobEntity.java`](geo-analytics/src/main/java/com/geo/analytics/domain/entity/JobEntity.java) | `@Column(name = "job_status", … length = 20)` を **新しい最大長に合わせて更新**。 |
| **新規 Flyway**（例: `V116__job_status_length_and_extracting.sql`） | `ALTER TABLE jobs ALTER COLUMN job_status TYPE VARCHAR(32);`（および **`jobs_aud`** 同様）。必要なら既存値のチェックなしで実行可能。Enum 追加のみでは Hibernate は対応、`BatchPersistenceService` 等の **`JobStatus.valueOf`** は DB に新文字列が入った後にのみ影響。 |
| **`project_competitors`** | **DROP しない**。フェーズ2.2 で抽出した 3 社 URL を保存する器として継続利用するのが自然（Flyway で DROP すると後続 Step が再設計になる）。 |
| **`JobStatusResponse`** | `jobStatus` は既に `name()` 文字列のため **新 Enum 値が自動で流出**。追加の DTO フィールドは必須ではない。 |

---

## 3. 既存テストへの影響

| ファイル | 予想影響 |
|---------|----------|
| [`SubscriptionIntegrationTest`](geo-analytics/src/test/java/com/geo/analytics/integration/SubscriptionIntegrationTest.java) | **競合 URL 入力とは無関係**な competitor アサーションが中心。**コンパイルエラーは概ねなし**。新ステータスを統合テストで明示する場合のみケース追加。 |
| その他 `src/test` | **`CreateJobRequest` に競合がないため**、DTO 削除による大量コンパイルエラーは **想定しにくい**。Enum 追加・列長変更のみなら既存テストはほぼそのまま。 |

**フロントエンド（スコープ外だが連鎖）**: [`JobAnalysisPage.tsx`](geo-analytics/frontend/src/pages/JobAnalysisPage.tsx) / [`ReportPrintPage.tsx`](geo-analytics/frontend/src/pages/ReportPrintPage.tsx) の「処理中」列挙に新ステータスが無いと UX がずれる → **バックエンド Step 1 完了後、別コミットで `EXTRACTING_COMPETITORS` を processing 扱いに追加**を推奨。

---

## 変更予定ファイル一覧（Step 1 実装時の候補）

1. [`geo-analytics/src/main/java/com/geo/analytics/domain/enums/JobStatus.java`](geo-analytics/src/main/java/com/geo/analytics/domain/enums/JobStatus.java) — Enum 定数追加  
2. [`geo-analytics/src/main/java/com/geo/analytics/domain/entity/JobEntity.java`](geo-analytics/src/main/java/com/geo/analytics/domain/entity/JobEntity.java) — `job_status` カラムの `length` 更新  
3. **新規** `geo-analytics/src/main/resources/db/migration/Vnnn__*.sql` — `jobs` / `jobs_aud` の `job_status` 長さ拡張  
4. （任意）[`JobController.java`](geo-analytics/src/main/java/com/geo/analytics/web/controller/JobController.java) — `streamJob` の新ステータス分岐  
5. （方針次第）[`JobProjectResponse.java`](geo-analytics/src/main/java/com/geo/analytics/web/dto/JobProjectResponse.java) — 読取 `competitor_urls` の維持または削除とマッピング変更  

**変更不要（現調査）**: `CreateJobRequest`、`ProjectSettingsPatchRequest`、`ProjectSettingsService` の競合入力削除（もともとなし）。

---

この計画でよろしければ、実装指示をください。

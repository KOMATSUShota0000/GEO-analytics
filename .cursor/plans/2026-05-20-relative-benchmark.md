# 仕様書: StrategyDashboard 相対評価セクション実データ化

- **作成**: CPO室 / 2026-05-20
- **対象スプリント**: Sprint6（dev-phase-log フェーズ6）
- **関連課題**: dev-status.md「実装中」§ RelativeEvaluationSection.tsx の `MOCK_ROWS` 固定

## 1. 背景・目的（What / Why）

`RelativeEvaluationSection.tsx` は `MOCK_ROWS` をハードコード表示している。これは
プロダクトの核②「完全ホワイトラベル対応の競合比較チャート＝実利」の心臓部であり、
**全契約先に同一の偽データが出る＝信頼即崩壊**。実データに置換する。

**コア原則の遵守:**
- 「スタブやモックで誤魔化さない」→ 3指標すべて既存の実テーブル由来に限定する
- 実データが無い場合は**偽値を出さず**「未解析」「上位プラン限定」状態を明示する
- 競合比較は Pro 機能（既存 `competitorShares` が `plan.usesProTierFeatures()` ゲート済みの方針に一致）

## 2. 実データ源（既存資産の再利用）

| 行 | 指標 | 実データ源 | self | competitor |
|----|------|-----------|------|-----------|
| 1 | AI言及シェア | `AnalyticsAggregationService.summarizeProject()` の `competitorShares` / `trendData` | 最新日 `trendData.somAvg` | `competitorShares` の最大 share |
| 2 | 構造化シグナル密度 | `audit_rubric_results` `criterion_id = MACHINE_READABILITY_SIGNAL`（max 25.0） | `is_self=true` の平均 score | `is_self=false` の平均 score |
| 3 | エンティティ解像度 | `audit_rubric_results` `criterion_id = ENTITY_BIOGRAPHY`（max 5.0） | 同上 | 同上 |

> いずれも `AiRubricAuditService`（フェーズ4実装済み）が書き込む実テーブル。新規データ生成は無い。

## 3. バックエンド設計（How）

### 3.1 エンドポイント
`GET /api/v1/projects/{projectId}/relative-benchmark`
`ProjectAnalyticsController` に追加。既存 `asset-snapshots` と同じ認可を流用：

```java
@PreAuthorize("@tenantAccessEvaluator.canReadProjectAssetSnapshots(authentication, #projectId)")
```
（プロジェクトスコープ＋ロールチェック済み。意味論が一致するため新規 evaluator は作らない。）

### 3.2 DTO（既存 snake_case 命名規約に準拠）
- `RelativeBenchmarkResponse(boolean locked, boolean available, List<RelativeBenchmarkRow> rows)`
  - `@JsonNaming(SnakeCaseStrategy)`（`AssetSnapshotsChartResponse` と同様）
- `RelativeBenchmarkRow(String label, String selfLabel, String competitorLabel, boolean gap)`
  - フロント既存 `BenchmarkRow` 型と1:1対応（テーブルUIを変えない）

### 3.3 サービス `RelativeBenchmarkService`（read-only）
`GeoAssetSnapshotQueryService` のテナントスコープ作法を踏襲：

1. `analyticsAggregationService.summarizeProject(projectId)` を呼ぶ（内部で自前テナントスコープ）
   - 空 → `available=false`（未解析）を返す
   - `plan = subscriptionPlan`。`!plan.usesProTierFeatures()` → `locked=true, rows=[]`
   - `selfSom` = `trendData` 末尾の `somAvg`、`compSom` = `competitorShares` の最大 share
2. `TenantContextHolder.getTenantId()` のスコープで rubric 集計：
   - `verifyProjectVisible(projectId)`（projects 存在チェック）
   - `jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)` で最新ジョブ
   - `auditHistoryRepository.findByJobId(jobId)` → 各 `auditHistoryId`
   - `auditRubricResultRepository.findByAuditHistoryId(id)` を集約し、対象 criterion の
     `is_self` 別平均スコアを算出
3. 3行を組み立て：
   - ラベルは正規化率（score / maxScore）から `良好`(≥70%) / `普通`(≥40%) / `要強化`(<40%)。
     行1のみ `42.0%` のような実数値文字列
   - `gap = self < competitor`（competitor 不在時は `gap=false`、competitorLabel=`データなし`）
   - 競合データもrubricも皆無 → `available=false`

### 3.4 リポジトリ
`AuditRubricResultRepository` に集計クエリを追加（N+1回避）：
```java
@Query("select r.criterionId as criterionId, r.isSelf as self, avg(r.score) as avgScore "
     + "from AuditRubricResultEntity r where r.auditHistoryId in :ids "
     + "and r.criterionId in :criteria group by r.criterionId, r.isSelf")
List<RubricBenchmarkAggregate> aggregateByCriterion(List<UUID> ids, List<String> criteria);
```
RLS で tenant_id は自動スコープ（`BaseTenantEntity`）。

## 4. フロントエンド設計

- `frontend/src/hooks/useRelativeBenchmark.ts` 新設（`useProjectAssetSnapshots` と同型：
  `apiFetch` + `responseJsonAsCamel`、loading/error/locked/available 状態）
- `RelativeEvaluationSection.tsx`：
  - `MOCK_ROWS` 削除
  - props で `rows / locked / available / loading / error` を受ける
  - `locked` → `LockedInsightCallout` 同等のぼかし＋「Proプランで競合比較が開示されます」
  - `available=false` → 「解析完了後に競合比較が表示されます」
  - `loading` → スケルトン、`error` → エラーボックス（既存配色トークン流用）
  - 説明文「今後のフェーズで連携予定です」を削除
- `StrategyDashboardPage.tsx`：`useRelativeBenchmark(projectId)` を呼び props 注入

## 5. 受け入れ基準（オーディター監査項目）

1. `./mvnw clean test` 全件 PASS（既存テスト退行なし）
2. `RelativeBenchmarkServiceTest` 追加：Pro/非Pro/未解析/競合なし の4分岐
3. `MOCK_ROWS` がコードベースから消滅（grep 検出ゼロ）
4. エンドポイントが `@PreAuthorize` でテナント保護されている
5. アーキ規約遵守：`@Transactional(readOnly=true)`、ThreadLocal/synchronized/parallel 不使用、
   テナントスコープは `TenantPlanScope`/`TenantContextHolder` 経由
6. 実データ皆無時に偽値を出さない（locked / available=false の分岐が動作）
7. ADR 作成（`docs/adr/` に本機能の設計判断記録）

## 6. スコープ外（別タスク）

- `/api/v1/projects/{id}/analytics` 自体の `@PreAuthorize` 欠落（既存issue・別チケット）
- 競合シェアの時系列推移グラフ化（将来フェーズ）

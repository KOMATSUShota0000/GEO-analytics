# ADR-034: 競合削除 Sprint C4 — 永続化撤去と job_competitor_scores テーブルDROP（不可逆）

## 日付
2026-06-07

## 状況
ADR-031〜033 の続き（C1 FE / C2 BE API / C3 抽出エンジン）。残るは競合スコアの永続化と DBテーブル `job_competitor_scores` の撤去。

**調査で確定した重要事実**:
- `job_competitor_scores` テーブルは **読み出しゼロの書き込み専用（死蔵）**。リポジトリも SELECT も存在しない（grep全確認）。
- ここに書かれていた `CompetitorScoreRow` は「AI回答内で共起した他エンティティ」のスコアで、**ユーザー向け『3競合表示』とは別物**。
- **GBVS相対スコア**（`gbvsNormalizedScore`）は `InformationTheoryBasedAggregator` がメモリ内で `CompetitorResult` から算出しており、本テーブルに**一切依存しない**。
- 結論: テーブルDROPはスコアに影響しない（安全）。ただし配線（`CompetitorScoreRow`）が検証パイプラインを貫通しているため、`CompetitorResult`/GBVSを温存しつつ `CompetitorScoreRow` 経路のみを解く。

## 決定（不可逆操作を含む）
- **Flyway V133**: `DROP TABLE IF EXISTS job_competitor_scores`（**不可逆**・本番DBに存在。オーナー承認済み）。
- 削除: `domain/entity/JobCompetitorScoreEntity`、`application/dto/CompetitorScoreRow`。
- `AuditHistoryEntity`: `competitorScores`（@OneToMany）リレーションと getter/setter・不要 import を除去。
- `JobPersistenceService`: `upsertAuditHistoryForJobQuery` から `competitorScoreRows` 引数と `applyCompetitorScores` メソッド・呼び出しを除去。
- `BatchPersistenceService`: `upsertAuditHistory` から `competitorScoreRows` 引数と `job_competitor_scores` への DELETE/INSERT を除去。
- `SyncVerificationResult`: `competitorScoreRows` フィールド除去。`SyncVerificationService`: `CompetitorResult`→`CompetitorScoreRow` マッピングを除去（`competitorResults()` 自体は GBVS 用に温存）。
- 呼び出し元修正: `JobQuerySubmissionService`・`JobSyncTestService`・`GeminiResultProcessor`（空 `List.of()` 引数）。
- `SubscriptionIntegrationTest`: `DELETE FROM job_competitor_scores` を除去し、`new SyncVerificationResult(...)` を新シグネチャに合わせた。

## 温存（誤削除でないこと）
`CompetitorResult`・`InformationTheoryBasedAggregator`・`GeminiVerificationAdapter` の competitorResults 構築・`VerificationResponse.competitorResults`（＝GBVS相対スコアの素）。`JobBenchmarkCaptureService`（自社監査/MEO/アラート）。業種判定・SerpAPI基盤・スコア。

## 理由
死蔵テーブルと、それにしか使われない `CompetitorScoreRow` 配線を Shadow Implementation を残さず撤去。GBVS は別経路（メモリ内集計）のため無影響。

## 結果
- `job_competitor_scores` テーブルと関連エンティティ/DTO/永続化経路が消滅。
- main+test コンパイル緑、`mvn test` BUILD SUCCESS（Testcontainers Postgres で V133 DROP 適用・GBVS含む全テストPASS）。
- 残: C5（`CompetitorProfile`/project競合フィールド・`EXTRACTING_COMPETITORS` ステータス・`JobBenchmarkCaptureService` の競合ループと `competitor_rubric_audits_json` 列・`CompetitorExtractionMode`→業種名リネーム・`ConsultantOutputData.competitorComparison`/`CompetitorShareEntry` 整理）。

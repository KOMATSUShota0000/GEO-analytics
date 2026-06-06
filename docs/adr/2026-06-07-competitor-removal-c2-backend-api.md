# ADR-032: 競合削除 Sprint C2 — BE API/集約から競合を除去

## 日付
2026-06-07

## 状況
ADR-031（競合比較廃止・Sprint C1でFE表示撤去済み）の続き。BE側で競合シェア集約と相対評価ベンチマークAPIがまだ存在し、FEから参照されない孤立コードになっていた。

調査で判明: `AnalyticsAggregationService.extractCompetitorShares` は `ConsultantOutputData.competitorComparison()`（AI応答内）から集計していたが、コンサルプロンプトは既に「competitorComparison を出力するな」と指示済みで実質常に空。`/relative-benchmark` エンドポイントのFE消費側（`useRelativeBenchmark`）はC1で削除済み。

## 決定
BEの競合シェア集約・相対評価ベンチマークを撤去した。

- `web/dto/AnalyticsSummaryResponse`: `competitor_shares` フィールド削除（trend_data / subscription_plan のみに）。
- `application/service/AnalyticsAggregationService`: `extractCompetitorShares` と競合シェア組み立てを削除。未使用となった `ObjectMapper` 依存・competitor系 import を除去。trend集計とプラン解決は維持。
- `web/controller/ProjectAnalyticsController`: `/relative-benchmark` エンドポイントと `RelativeBenchmarkService` 依存を削除。
- ファイル削除: `RelativeBenchmarkService`・`RelativeBenchmarkResponse`・`RelativeBenchmarkRow`・`web/dto/CompetitorSharePoint`・`RelativeBenchmarkServiceTest`。

**温存（C3以降で精査）**: `JobAnalysisBenchmarkAssembler`/`RubricBenchmarkGapService` は `JobController`・感情アラート・タスク再生成に織り込まれており、競合比較ではなくルーブリックギャップ系の可能性が高いため本C2では触らない（R3回避）。`CompetitorShareEntry` は `ConsultantOutputData` がまだ参照するため残置（C5でスキーマ整理）。

## 理由
- 孤立した競合API/集約は GEO=絶対評価の方針に反する死蔵コード。Shadow Implementation を残さず段階的に除去。
- ベンチマークアセンブラ系はアドバイス/アラートに波及するため、安全のため分離してC3で精査する（中枢を一度に壊さない）。

## 結果
- `analytics` 応答から competitor_shares が消え、relative-benchmark エンドポイントが廃止された。
- main+test コンパイル緑、`mvn test` 228件 全PASS（0 failures/0 errors）。RelativeBenchmarkServiceTest を削除（対象機能の消滅に伴う）。
- 残: C3（抽出エンジン削除・JobQuerySubmissionService手術）、C4（永続化＋DBテーブルDROP・不可逆）、C5（cleanup/リネーム）。

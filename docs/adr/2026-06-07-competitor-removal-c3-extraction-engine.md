# ADR-033: 競合削除 Sprint C3 — 競合抽出エンジンの削除と中枢の手術

## 日付
2026-06-07

## 状況
ADR-031（廃止方針）/ADR-032（C2: API・集約除去）の続き。競合抽出エンジン（SERP/合成/AIフィルタ）本体と、それを呼ぶ `JobQuerySubmissionService` の経路が残っていた。

調査で確定した最重要事実: `JobQuerySubmissionService.runHybridContinuation` で抽出した競合(`selected`)は **project の競合URL/プロファイル保存にしか使われず**、クエリ生成・スコア・AIプロンプトには一切影響しない（`continueAfterCompetitorsPersisted` は `selected` を引数に取らない）。＝**競合抽出は表示専用の副次経路**であり、除去しても解析挙動は不変。

また `JobBenchmarkCaptureService` は競合専用ではなく、自社ルーブリック監査・MEO実測（スコア入力）・感情アラート生成も担うため**温存**（C2監査でも確認）。

## 決定
競合抽出エンジン一式を削除し、中枢を抽出非依存に手術した。

- `JobQuerySubmissionService`: `runHybridContinuation` から競合抽出（`hybridCompetitorPipelineService.executePipeline`）と `saveProjectCompetitorUrls/Profiles` を除去し、直接 `continueAfterCompetitorsPersisted` へ。失敗時のクォータ返金＋FAILED遷移は維持。`competitorProfilesFromSelected`/`competitorUrlsFromSelected` ヘルパーと competitor 系 import/フィールドを削除。
- 削除（18ファイル）: `HybridCompetitorPipelineService`・`CompetitorExtractionStrategy`(IF)・`CompetitorExtractionStrategyFactory`・`LocalStoreStrategy`・`CorporateServiceStrategy`・`OnlineServiceStrategy`・`SerpJobCompetitorExtractor`・`SyntheticSelectedCompetitorFactory`・`CompetitorFilterService`・`CompetitorFilterPrompts`・`CompetitorFilterOutputSchema`・`CompetitorFilterAiSelection`・`GeminiCompetitorInferenceAdapter`・`CompetitorInferencePrompts`・`CompetitorInferenceResult`・`CompetitorExtractionContext`・`SelectedCompetitor`＋`SyntheticSelectedCompetitorFactoryTest`。
- `AiConfig`: 競合フィルタ用 Gemini ビーン(`GEMINI_COMPETITOR_FILTER_MODEL`)・定数・import を除去。
- `SubscriptionIntegrationTest`: 不要化した `HybridCompetitorPipelineService` モック・`SelectedCompetitor` スタブ・import を除去（job フローは抽出をスキップして成立）。

## 理由
- 抽出は表示専用の副次経路と確定したため、本流（クエリ→スコア）に影響なく安全に除去できる。
- 合成競合（synthetic）パディングという「形だけ」の元凶を根絶。
- `JobBenchmarkCaptureService` 本体・SerpAPI基盤・業種判定 `CompetitorExtractionMode`・スコアは温存し、自社評価/権威/アラートを保全。

## 結果
- 競合抽出が解析パイプラインから消滅。`EXTRACTING_COMPETITORS` ステータスは当面維持（実体は即continuation。リネームはC5）。
- main+test コンパイル緑、`mvn test` BUILD SUCCESS（0 failures/0 errors）。挙動不変を確認。
- 残: C4（永続化 `JobCompetitorScoreEntity`・`persistJobBenchmarkSnapshot` の competitor 引数・DBテーブル `job_competitor_scores` のDROP＝**不可逆**・本番DB存在）、C5（competitorループ整理・`CompetitorProfile`/project競合フィールド除去・`CompetitorExtractionMode`→業種名リネーム・ステータス整理）。
- C4はオーナー確認の上で着手する。

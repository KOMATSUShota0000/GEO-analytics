---
name: one-click-competitor-extraction
overview: フェーズ2.2「ワンクリック真・競合抽出」のバックエンド中核を、既存パイプライン（HybridCompetitorPipelineService）と並列の新規ユースケースとして実装。自社URL→本文クロール→業種・商圏LLM推論→Places 1-Shot 検索→AI巨人フィルタで3社厳選（理由付き）→不足時の仮想ベンチマーク補完→project_competitors 永続化→同期 REST で即時返却、までを直列同期処理（仮想スレッド前提）で構成する。
todos:
  - id: domainEnumAndDtos
    content: BenchmarkSource enum と BenchmarkCandidate / GiantFilterRawResult / OneClickCompetitorExtractionResult を record で新規作成
    status: pending
  - id: giantFilterAi
    content: GiantFilterPrompts / GiantFilterOutputSchema / GeminiGiantFilterAdapter（@CreditReservation + @Lazy 自己注入）を実装
    status: pending
  - id: virtualFallback
    content: VirtualBenchmarkProvider を実装（業種別固定テンプレートで `requiredCount` を返す純関数）
    status: pending
  - id: useCaseOrchestration
    content: OneClickCompetitorExtractionService を実装（直列同期＋全例外吸収＋virtual フォールバック判定＋3件永続化）
    status: pending
  - id: webLayer
    content: OneClickCompetitorExtractionRequest / BenchmarkResponse / OneClickCompetitorExtractionResponse + OneClickCompetitorController を実装
    status: pending
  - id: buildVerify
    content: ./mvnw.cmd compile + ReadLints で全クラスの整合確認
    status: pending
isProject: false
---


# One-Click Competitor Extraction Plan

## 1. 確認済み既存資産（再利用）

- 本文取得: [WebCrawlerPort#extractContent(String url)](geo-analytics/src/main/java/com/geo/analytics/application/port/WebCrawlerPort.java) → `CrawledPageData`（実装は `PlaywrightWebCrawlerAdapter` + `JsoupPageExtractor` フォールバック）
- 業種・商圏推論: [GeminiCompetitorInferenceAdapter#infer(PageSignalsForInference)](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/GeminiCompetitorInferenceAdapter.java) → `CompetitorInferenceResult(industry, location, evidence)`（**未配線**を本計画で配線）
- 競合 20 件取得: [GooglePlacesAdapter#searchCompetingBusinesses(UUID, String, IndustryType)](geo-analytics/src/main/java/com/geo/analytics/infrastructure/api/GooglePlacesAdapter.java) → `List<ExtractedPlace>`（**未呼び出し**を本計画で配線）
- 永続化: `JobPersistenceService#saveProjectCompetitorUrls(projectId, urls)`（最大 3 件、`ProjectEntity.competitorUrls`）
- AI 構造化出力パターン: [RubricAuditOutputSchema](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/RubricAuditOutputSchema.java), [RemediationTaskOutputSchema](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/RemediationTaskOutputSchema.java)
- クレジット AOP: 既存 `AiRemediationService` 自己注入＋`@CreditReservation` パターン

## 2. データフロー（同期・仮想スレッド前提）

```mermaid
flowchart TD
  Client[POST /api/projects/{projectId}/competitors/extract]
  Ctrl[OneClickCompetitorController]
  UC[OneClickCompetitorExtractionService.extract]
  Crawl[WebCrawlerPort.extractContent]
  Infer[GeminiCompetitorInferenceAdapter.infer]
  Places[GooglePlacesAdapter.searchCompetingBusinesses]
  Filter[GeminiGiantFilterAdapter.filterToBenchmarks]
  Fallback[VirtualBenchmarkProvider.generateDefaults]
  Persist[JobPersistenceService.saveProjectCompetitorUrls]
  Resp[OneClickCompetitorExtractionResponse]

  Client --> Ctrl --> UC
  UC --> Crawl --> Infer --> Places --> Filter
  Filter -->|"size >= 3"| Persist
  Filter -->|"size < 3 or error"| Fallback --> Persist
  Persist --> Resp
```

## 3. 新規・修正クラス一覧

### 3.1 ドメイン enum

**新規** [domain/enums/BenchmarkSource.java](geo-analytics/src/main/java/com/geo/analytics/domain/enums/BenchmarkSource.java)
- `LIVE_PLACES` / `VIRTUAL_FALLBACK`

### 3.2 アプリケーション層 record（DTO）

**新規** [application/dto/BenchmarkCandidate.java](geo-analytics/src/main/java/com/geo/analytics/application/dto/BenchmarkCandidate.java)
- `record BenchmarkCandidate(String name, String websiteUrl, Double rating, Integer reviewCount, BenchmarkSource source, String selectionReason)`

**新規** [application/dto/GiantFilterRawResult.java](geo-analytics/src/main/java/com/geo/analytics/application/dto/GiantFilterRawResult.java)
- LLM 直生 JSON マッピング用 record（`record Item(String name, String websiteUrl, Double rating, Integer reviewCount, String selectionReason)` + `record GiantFilterRawResult(List<Item> selected)`）

**新規** [application/dto/OneClickCompetitorExtractionResult.java](geo-analytics/src/main/java/com/geo/analytics/application/dto/OneClickCompetitorExtractionResult.java)
- `record OneClickCompetitorExtractionResult(IndustryType inferredIndustry, String inferredLocation, String inferenceEvidence, List<BenchmarkCandidate> benchmarks, boolean usedFallback)`

### 3.3 AI 系（infrastructure.ai）

**新規** [infrastructure/ai/GiantFilterPrompts.java](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/GiantFilterPrompts.java)
- `static String systemPrompt()` — 自社業種／商圏／URL を踏まえ、大手ポータル・全国チェーン・SEO比較サイトを除外し、**同規模の現場ライバルを最大 3 社**選定。各社に **selectionReason**（日本語、120 字以内）を必須化。GEO/SEO 順位や検索ボリュームには言及しない、を明文化。
- `static String userMessage(IndustryType industry, String location, String selfUrl, String candidatesJson)` — Jackson でシリアライズした `ExtractedPlace` 一覧を含む。

**新規** [infrastructure/ai/GiantFilterOutputSchema.java](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/GiantFilterOutputSchema.java)
- `static ResponseFormat giantFilterResponseFormat()` — `JsonObjectSchema` で `selected` 配列（max 3, `additionalProperties=false`）を強制。`RubricAuditOutputSchema` と同じパターン。

**新規** [infrastructure/ai/GeminiGiantFilterAdapter.java](geo-analytics/src/main/java/com/geo/analytics/infrastructure/ai/GeminiGiantFilterAdapter.java)
- `@Component`、`@Qualifier(AiConfig.GEMINI_25_FLASH) ChatLanguageModel`、`ObjectMapper`、`@Lazy GeminiGiantFilterAdapter self` を注入
- 公開: `List<BenchmarkCandidate> filterToBenchmarks(UUID projectId, IndustryType selfIndustry, String selfLocation, String selfUrl, List<ExtractedPlace> rawCandidates)`
  - 入力検証 → 内部で `self.invokeLlmWithCreditReservation(...)` を呼ぶ
- AOP 委譲: `@CreditReservation(amount = 60L, settleNote = "ai_giant_filter") public GiantFilterRawResult invokeLlmWithCreditReservation(UUID projectId, String systemPrompt, String userMessage)` — `chat` 1 回 + `objectMapper.readValue(...)`
- 戻り変換時に `BenchmarkSource.LIVE_PLACES` を付与
- 例外: `IllegalStateException` 等は呼び出し側でフォールバックに振り替えるためそのまま伝搬

### 3.4 フォールバック

**新規** [application/service/VirtualBenchmarkProvider.java](geo-analytics/src/main/java/com/geo/analytics/application/service/VirtualBenchmarkProvider.java)
- `@Component`、純関数
- `List<BenchmarkCandidate> generateDefaults(IndustryType industry, String location, int requiredCount)`
- `IndustryType` 別の固定マップを `static final Map<IndustryType, List<TemplateBenchmark>>` で保持（`OTHER` は汎用の3社）
- 各候補は `websiteUrl=""`, `rating=null`, `reviewCount=null`, `source=VIRTUAL_FALLBACK`, `selectionReason="実在ライバルが不足のため、業種一般の基準を仮想ベンチマークとして提示"`

### 3.5 ユースケース（オーケストレーション）

**新規** [application/service/OneClickCompetitorExtractionService.java](geo-analytics/src/main/java/com/geo/analytics/application/service/OneClickCompetitorExtractionService.java)
- `@Service`、依存: `WebCrawlerPort`, `GeminiCompetitorInferenceAdapter`, `GooglePlacesAdapter`, `GeminiGiantFilterAdapter`, `VirtualBenchmarkProvider`, `JobPersistenceService`
- 定数: `REQUIRED_BENCHMARK_COUNT = 3`
- 公開: `OneClickCompetitorExtractionResult extract(UUID projectId, String selfUrl)`
- ステップ:
  1. 入力検証（`projectId`、`selfUrl` blank チェック）
  2. クロール: `WebCrawlerPort.extractContent(selfUrl).mainContent()`。失敗時は `industry=OTHER, location=""` で即フォールバック（`usedFallback=true`、3 件全て virtual）
  3. 推論: `GeminiCompetitorInferenceAdapter.infer(new PageSignalsForInference(content, selfUrl))`。`location` が空なら virtual 即返却
  4. 1-Shot 検索: `GooglePlacesAdapter.searchCompetingBusinesses(...)`。例外時は virtual へ
  5. AI 巨人フィルタ: `GeminiGiantFilterAdapter.filterToBenchmarks(...)`。例外・空時は live=空扱い
  6. マージ: `live.size() < 3` のとき `VirtualBenchmarkProvider.generateDefaults(industry, location, 3 - live.size())` で補充。`usedFallback = !virtual.isEmpty()`
  7. 永続化: `live` の websiteUrl のみ抽出して `jobPersistenceService.saveProjectCompetitorUrls(projectId, liveUrls)`（virtual は URL 空のため保存対象外）
  8. `OneClickCompetitorExtractionResult` を構築して返却
- 例外ハンドリング: 各段階で `try/catch (RuntimeException)` し `log.warn(...)` → 既存 `truncateStackTrace` 風に短縮（必要なら共通化）。**全例外を吸収して 200 + `usedFallback=true` で返す**ことを基本ポリシーとする
- ScopedValue: コントローラ前段で `TenantPlanScope` バインド済みのため、追加バインドはしない（既存パターン踏襲）。`saveProjectCompetitorUrls` が `TenantPlanScope.executeWithTenant*` を内部で要求する場合のみ、その境界で追加検討
- 仮想スレッド：直列処理のため `StructuredTaskScope` は使用しない。Tomcat スレッド（VT 化済み or プール）上で同期処理

### 3.6 Web 層

**新規** [web/dto/OneClickCompetitorExtractionRequest.java](geo-analytics/src/main/java/com/geo/analytics/web/dto/OneClickCompetitorExtractionRequest.java)
- `record OneClickCompetitorExtractionRequest(String selfUrl)` + Bean Validation（`@NotBlank`, `@Pattern` で http/https）

**新規** [web/dto/BenchmarkResponse.java](geo-analytics/src/main/java/com/geo/analytics/web/dto/BenchmarkResponse.java)
- `record BenchmarkResponse(String name, String websiteUrl, Double rating, Integer reviewCount, String source, String selectionReason)`

**新規** [web/dto/OneClickCompetitorExtractionResponse.java](geo-analytics/src/main/java/com/geo/analytics/web/dto/OneClickCompetitorExtractionResponse.java)
- `record OneClickCompetitorExtractionResponse(IndustryType inferredIndustry, String inferredLocation, String inferenceEvidence, List<BenchmarkResponse> benchmarks, boolean usedFallback)`
- `static OneClickCompetitorExtractionResponse from(OneClickCompetitorExtractionResult)` — application 層 record → web 層 record の変換（enum→String 変換含む）

**新規** [web/controller/OneClickCompetitorController.java](geo-analytics/src/main/java/com/geo/analytics/web/controller/OneClickCompetitorController.java)
- `@RestController @RequestMapping("/api/projects/{projectId}/competitors")`
- `@PostMapping("/extract") OneClickCompetitorExtractionResponse extract(@PathVariable UUID projectId, @Valid @RequestBody OneClickCompetitorExtractionRequest req)`
- セキュリティ: 既存 `JobController` の `@PreAuthorize` パターン踏襲（テナント／プロジェクト所有権チェック）
- 例外マッピング: ユースケース内で吸収するため `@ExceptionHandler` 追加は最小限

## 4. アーキテクチャ厳守事項の遵守

- コード内コメント禁止: 全新規ファイルで Javadoc／行コメントゼロ
- データ保持クラスは record: 全 DTO（`BenchmarkCandidate`, `GiantFilterRawResult`, `OneClickCompetitorExtractionResult`, `OneClickCompetitorExtractionRequest`, `BenchmarkResponse`, `OneClickCompetitorExtractionResponse`）を record で定義
- 仮想スレッド前提・同期処理: `CompletableFuture` / `@Async` / `StructuredTaskScope` は不使用。各段階を直列同期で呼ぶ
- LangChain4j 構造化出力: `GiantFilterOutputSchema` で `ResponseFormat`+`JsonSchema`（既存 `RubricAuditOutputSchema` と同パターン）。`@StructuredPrompt` は既存コードベースで使用例なしのため不採用

## 5. 仕様逸脱・要確認ポイント

- **クレジット消費量**: 既存比（rubric=80, remediation=200, places=30）から鑑みて `ai_giant_filter = 60` を提案。要承認
- **永続化対象**: 仮想ベンチマーク（URL 空）は `project_competitors` に**保存しない**方針（既存 `saveProjectCompetitorUrls` が URL を要求するため）。selectionReason はレスポンスのみで永続化なし（永続化したい場合は別チケットで `project_competitors` テーブル拡張）
- **`location` 空時**: `searchCompetingBusinesses` は `IllegalArgumentException` を throw する仕様。本計画ではユースケース内で**空判定して即 virtual フォールバック**することで 500 を回避
- **テナントコンテキスト**: 仮想スレッド固有の追加バインドは行わない（コントローラ層既設の前提）。`saveProjectCompetitorUrls` 内部で要求される場合のみ調整
- **エラー時のレスポンス**: 「全例外吸収して 200 + virtual」が基本だが、入力検証失敗（projectId 不正、URL 形式不正）は `400 Bad Request` を維持

## 6. 実装順序（micro-contract 単位）

1. `BenchmarkSource` enum + `BenchmarkCandidate` / `GiantFilterRawResult` / `OneClickCompetitorExtractionResult` records
2. `GiantFilterPrompts` + `GiantFilterOutputSchema` + `GeminiGiantFilterAdapter`（自己注入＋`@CreditReservation`）
3. `VirtualBenchmarkProvider`（業種別固定テンプレート）
4. `OneClickCompetitorExtractionService`（オーケストレーション、エラー吸収＋フォールバック判定）
5. `OneClickCompetitorExtractionRequest` / `BenchmarkResponse` / `OneClickCompetitorExtractionResponse` records
6. `OneClickCompetitorController`（同期 POST 1 本）
7. ローカルでコンパイル確認（`./mvnw.cmd compile`）+ ReadLints

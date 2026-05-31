# ADR-011: AI 議論駆動アドバイス生成への移行（Sprint 1 / Free プランパス）

## 日付

2026-05-30

## 状況

ジョブ全体の戦略診断（`AuditHistoryEntity.diagnosticMessage` + `recommendedActions`）は、`StrategyInsightService` 内の **改 Z' スコアによる4分類テンプレート分岐** で生成されていた。

```
+2σ以上     → MSG_DOMINATOR
+0.5σ以上   → MSG_STRONG
-1σ以上     → MSG_REDOCEAN
それ以下    → MSG_BLINDSPOT
```

この設計はオーナーの不満点として表出した：

- 業種・ターゲット・自社の強みに関係なく、同じスコア帯のユーザーには**完全に同じ文言**が返る
- プロダクトの核①「WOW 体験」に直結する 4 ペルソナ AI 議論（`DebateOnboardingOrchestrator`）の知見が**最終アドバイスに一切反映されていない**
- ユーザー実感: 「金太郎飴的なテンプレ感」「AI が議論したはずなのに、結局テンプレ」

加えて、Sprint 1.0 の影響範囲調査で **`rollupJob` が同一ジョブで 2 回呼ばれていた**ことが判明した：

1. `GeminiResultProcessor.processOutputJsonlAndUpsertResults` (旧 line 163-167) — 暫定書き込み
2. `GapAnalysisService.runForJob` (line 61) — 上書き

両方を AI 駆動化すると **LLM 2 回呼び出し = チケット 2 倍消費** となり、プロダクトの核④「限界利益率 86% 死守」を破壊する。

## 決定

### 1. AI 議論駆動アドバイスへ移行

ジョブ全体アドバイスの生成を、テンプレート分岐から **DIRECTOR LLM（temperature 0.2）による JSON 生成** に置き換える。

- 新サービス `DebateAdviceGeneratorService` を新設
- 入力: `ProjectAdviceContext`（業種・ターゲット・強み）、解析統計（中央値改 Z'、Stage）、テンプレ4分類の該当文言を「方向性ヒント」として
- 出力: JSON で `diagnostic_message`（300 文字以内）、`recommended_actions`（3 件、各 60 文字以内）
- DIRECTOR ビーンを採用した理由: temperature 0.2（安定）かつオーケストレーション志向のキャラ設定が「複数情報の統合」に最適

### 2. テンプレートを「方向性ヒント」として活用

既存テンプレ 4 分類は **削除せず保持**し、LLM プロンプトに参考文として渡す。AI には「方向性は外さないが、業種・ターゲット・強みに即した固有のアドバイスを生成」させる。これにより：

- 既存資産（経営戦略コンサル的な構造化された 4 分類）を捨てずに活かす
- LLM 障害時にそのままフォールバックとして使える

### 3. LLM 障害時の明示的フォールバック

`StrategyInsightService.rollupJob(rows, project, plan)` は内部で try-catch して、失敗時は `rollupJobFromTemplate` に落とす。フォールバック発動は `SECURITY_AUDIT` ロガーに `source=AI` / `source=TEMPLATE_FALLBACK` の形で記録し、後から発動率を集計できるようにする。

### 4. `rollupJob` 2 度書きの解消

`GeminiResultProcessor` 側の `rollupJob` 呼び出しと `updateJobStrategyRollup` 書き込みは**完全に削除**する。ロールアップは `GapAnalysisService.runForJob` の 1 箇所のみに集約する。

### 5. 仕様書（CPO Plan）からの設計方針変更

CPO 仕様書（`.cursor/plans/2026-05-30-debate-driven-advice.md`、事実-1 対策方針）は以下の 2 段階構成を提案していた：

- `GeminiResultProcessor` 側: テンプレ版で即時暫定アドバイスを表示（待ち時間 UX を埋める）
- `GapAnalysisService` 側: AI 駆動で本番アドバイスに**上書き**

しかし Sprint 1 実装時のオーナーとの議論で、**「ユーザーが読んでる最中に内容が黙って変わる」のは SaaS UX アンチパターン**と判断し、以下の 1 段階構成に変更した：

- `GeminiResultProcessor` 側: 何も書かない（`strategy_rollup` は空のまま）
- フロント側: `LoadingCharacter`（紫ロボット UI）で「AI アナリストが議論中」を明示
- `GapAnalysisService` 側: AI 駆動で 1 回だけ書き込み、UI が完成版に切り替わる

これは「テンプレ→AI の黙々上書き」より「分析中→完成」の方が**信頼感が出る**という UX 判断による。LLM 失敗時は明示的なテンプレフォールバックで「簡易分析モード」を表示する設計（F-3.1）に切り替えた。

## 影響

### コスト

Free プランでも 1 ジョブにつき **DIRECTOR LLM 1 回呼び出し**が発生する。Gemini 2.5 Flash の単価でジョブ全体アドバイス 1 件あたりの追加コストは試算で 0.1〜0.3 円程度。1 解析 = 1 チケット消費（核④）の経済性に対しては許容範囲だが、Sprint 2 で Pro/Expert の短縮版議論を追加する際は要再試算（仕様書 F-5）。

### 品質

- 業種・ターゲット・強みに固有性のあるアドバイスが生成される
- LLM 障害時はテンプレフォールバックで「簡易分析モード」バッジ付き表示（破綻なし）
- 二度書きが解消され、`strategy_rollup` の書き込み主体が `GapAnalysisService` 1 箇所に明確化

### テスト

- 新規ユニットテスト 15 件で正常系・例外系・サニタイゼーション・フォールバックをカバー
- 既存回帰: 190 件中 189 件パス。失敗 1 件（`SubscriptionIntegrationTest.scenarioC`）は既存の test order dependency で本変更と無関係（単独実行は成功確認済み）
- `SECURITY_AUDIT` ログで `source=AI` / `source=TEMPLATE_FALLBACK` を出力。フォールバック発動率の事後集計が可能

### UX

- 解析完了直後、`strategy_rollup` が空である数秒〜十数秒間、紫ロボット LoadingCharacter で「4 人の AI アナリストが議論中...」を表示
- 既存の他箇所 LoadingCharacter と**同じビジュアル**になり、世界観が崩れない

## 関連する核

- **核①「WOW 体験」**: 4 ペルソナ議論の知見をアドバイスに反映し、固有性のあるアドバイスで WOW を取り戻す
- **核④「高利益率 86%」**: 2 度書き解消で LLM 1 回呼び出しに集約。チケット消費は据え置き

## Sprint 2 以降への申し送り

- Pro/Expert の短縮版議論起動（仕様書 F-2）と、それに伴うチケット消費 0.2 追加（仕様書 F-5）
- Teaser UI による Pro 誘導（仕様書 F-4）
- 個別クエリ用 `fromModifiedZ` / `keywordInsightRelative` の AI 化（次フェーズスコープ）
- `retryGapBatchForJob` (line 158) は現在 `rollupJob(rows)` 旧シグネチャ（テンプレのみ）を呼んでおり、これは Gap バッチ用 `trendClip` 構築目的で `strategy_rollup` の永続化には影響しないため Sprint 1 では対象外。AI 化する場合は別 Sprint で扱う

---

# ADR-012: Pro/Expert 短縮版議論起動とチケット消費（Sprint 2）

## 日付

2026-05-30

## 状況

Sprint 1 で Free パス（議論なし単発 DIRECTOR 生成）を実装した。Sprint 2 では仕様書 F-2 / F-5 に従い、
Pro/Expert プランで「解析ごとに新規 4 ペルソナ議論を起動し、その結論をアドバイスへ反映」する。
着手前にオーナー指示で限界利益率 86% の死守可否を試算した。

## 決定

### 1. 短縮版議論は 2 ターン上限をハード固定

`DebateOnboardingOrchestrator`（5 ターン・SSE 駆動・GEO-IG/較正カーネル付き）を直接流用せず、
`DebateAdviceGeneratorService` 内に SSE なしの軽量短縮版ランナー `runShortDebate` を新設した。
ペルソナ別 ChatLanguageModel ビーン（ANALYST/INNOVATOR/SKEPTIC）と `DebatePersonaSystemPrompts` は流用。
1 ターン = ANALYST→INNOVATOR→SKEPTIC の 3 呼び出し、2 ターンで 6 呼び出し + 後段 DIRECTOR 1 = **計 7 LLM 呼び出し**。
`SHORT_DEBATE_TURNS = 2` を定数でハード固定した理由は下記コスト試算の生命線が「履歴トークンの肥大抑制」にあるため。

### 2. チケット消費 0.2 = 200 単位（既存整数台帳で表現）

クレジット台帳（`OrganizationEntity.creditBalance` / `CreditVaultService.reserve(projectId, long)`）は
**1 解析 = `ONBOARDING_CREDIT` = 1,000 単位**スケールの整数。よって 0.2 チケット = **200 単位**
（`REMEDIATION_CREDIT = 200L` と同値）で端数なく表現できる。`reserve`→（成功時）`settle`／（失敗時）`refund` の
二相パターンを踏襲した。`DEBATE_CREDIT = 200L`。

### 3. 非同期スレッドでのテナントコンテキスト確立

`GapAnalysisService.runForJob` は生の仮想スレッド（`Executors.newVirtualThreadPerTaskExecutor`）で走り、
`TenantContextHolder.CONTEXT`（`ScopedValue`）が**未バインド**。一方 `CreditVaultService` は `requireOrgId()` で
コンテキストを要求する。そこで `ProjectAdviceContext` に `projectId`/`workspaceId`/`organizationId` を追加し
（`BatchPersistenceService.findProjectAdviceContext` が projects⋈workspaces を 1 クエリで解決・`@GlobalAccess`）、
議論起動時に `ScopedValue.where(TenantContextHolder.CONTEXT, identity).call(...)` でスコープを確立してから
reserve/settle を行う。`StaleReservationSweeper` と同じ非同期課金パターン。

### 4. 失敗時は全額返金 → Free パスへフォールバック（オーナー決定）

議論起動後に議論 LLM or DIRECTOR 生成が失敗した場合、`refund` で**全額返金**し（LLM コストは当社負担）、
議論なし単発 DIRECTOR（Free パス）へフォールバックする（仕様書 F-2）。それも失敗した場合は
`StrategyInsightService` 側のテンプレフォールバックに落ちる。「起動したが失敗 → 課金」は課金体験の信頼を損なうため
返金を選択した（2026-05-30 オーナー決定）。

## コスト試算（F-5 根拠）

- 単価想定（保守的）: Gemini 2.5 Flash $0.30/1M(in)・$2.50/1M(out)、¥155/$
- 7 呼び出し: 入力 21,000 tokens（履歴累積 平均3,000×7）+ 出力 5,600 tokens（平均800×7）
  → $0.0063 + $0.0140 = **約 $0.020 ≒ ¥3.1／解析**（強気見積りでも ≒ ¥7）
- 86% 死守条件: 増分コスト ≤ 0.14 × 0.2R → **R（1 チケット単価）≥ ¥179**。コスト 3 倍でも R ≥ ¥536。
  B2B 代理店向け単価は確実にこれを上回るため **86% は余裕で死守**。0.2 は取りすぎ気味で将来 0.1 へ下げる調整余地あり。

## 影響

- Pro/Expert の 1 解析あたり LLM 呼び出しが +7、追加コスト ≒ ¥3〜7。チケットは 1.0 → 1.2 消費。
- 短縮版は 2 ターン上限固定。これを緩める場合は本 ADR のコスト試算を必ず再実施すること。
- `ProjectAdviceContext` 拡張は後方互換（3 引数コンストラクタ維持）。既存 STANDARD パスは挙動不変。

## テスト

- `DebateAdviceGeneratorServiceTest` を 12 件に拡張（既存 9 + 新規 3）。全件パス。
  - Pro: 短縮版議論起動（ペルソナ 6 呼び出し）+ reserve/settle 検証
  - Pro: 議論失敗 → refund + Free フォールバック、settle されないこと
  - Pro: 課金識別子なし → 議論起動せず課金なし（STANDARD 相当）
- `DebateAdviceCreditIntegrationTest`（Testcontainers / PostgreSQL 17）2 件パス（N-2）。
  - Pro: 実 DB で credit_balance が DEBATE_CREDIT 分減少 + SETTLE 取引記録 + REFUND なし
  - Pro: 議論失敗 → 全額返金（残高不変）+ REFUND 取引記録 + Free フォールバック
  - あわせて `findProjectAdviceContext` の projects⋈workspaces（tenant_id::uuid）SQL を実スキーマで検証

## 実装中に検出・修正したバグ（重要）

`BatchPersistenceService.findProjectAdviceContext` の初版で **存在しない `projects.workspace_id` 列**を
JOIN・射影に使っていた（コンパイルは通るが Pro 解析時に実行時エラー）。実際は `projects.tenant_id`
（VARCHAR(36) に workspace UUID を文字列保持。`BaseTenantEntity.getWorkspaceId()` が `UUID.fromString`）で
リンクするのが正。JOIN/射影とも `p.tenant_id::uuid` に修正し、integration test で実スキーマ検証済み。

---

# ADR-013: フロント仕上げ — Teaser UI と簡易分析モードバッジ（Sprint 3）

## 日付

2026-05-31

## 状況

Sprint 1/2 でバックエンドの議論駆動アドバイス（Free 単発・Pro 短縮版議論）が完成。Sprint 3 では
ユーザーが価値を体感し課金導線に乗る UI（仕様書 F-3 / F-3.1 / F-4）を仕上げる。

## 決定

### 1. F-3 ローディング UI は Sprint 1 実装済みを確認

`JobAnalysisPage` は `strategy_rollup` 未確定の間 `<LoadingCharacter messages={AI_ADVICE_LOADING_MESSAGES} />`
を表示済み（「4人のAIアナリストが議論を始めています...」等）。Sprint 3 で追加実装は不要。

### 2. F-4 Teaser UI（Free → Pro 誘導）

新規 `frontend/components/DebateAdviceTeaserBanner.tsx` を追加。戦略診断ブロック下に、Free（STANDARD）時のみ
4 ペルソナ議論のぼかしプレビュー＋南京錠（Lock）＋「Pro限定」バッジ＋CTA を表示し `/pricing` へ遷移。
`isProPlanUi` が false のときだけ描画（核③ SaaS グロース）。

### 3. F-3.1 簡易分析モードバッジ（バックエンド縦スライス）

フォールバック発動（テンプレ表示）をユーザーに正直に提示するため、生成元をエンドツーエンドで露出した：

- 新 enum `AdviceSource { AI, TEMPLATE_FALLBACK }`
- `StrategyInsightService.rollupJobWithSource` が生成元込みで返す（`rollupJob` は委譲）
- `GapAnalysisService.runForJob` が生成元を `updateJobStrategyRollup(..., adviceSource)` で永続化
- Flyway `V128`：`jobs.job_advice_source VARCHAR(32)`（既存行は NULL = バッジなし）
- `JobEntity.jobAdviceSource` / `JobStatusResponse.adviceSource`（snake: `advice_source`）で露出
- フロント：`adviceSource === "TEMPLATE_FALLBACK"` のとき戦略診断ブロックに amber バッジ
  「簡易分析モード」＋ツールチップ「AI議論の生成に失敗したため、基本テンプレートで表示しています」

`updateJobStrategyRollup` は `COALESCE(?, job_advice_source)` で null 引数時に生成元を上書きしない
（テンプレ確定パス等の後方互換）。

## テスト

- 既存単体（DebateAdviceGeneratorServiceTest 12 / StrategyInsightServiceTest 7）全パス
- `DebateAdviceCreditIntegrationTest` 2 件パス。V128 適用済みスキーマで `ddl-auto=validate` 32 migrations 検証成功
  （`JobEntity.jobAdviceSource` ↔ `job_advice_source` マッピング健全性を担保）
- フロント `tsc --noEmit`：本変更分は型クリーン（既存の `JobAnalysisPage:328 requestPdfReport` 未使用のみ・本件外）

## 品質監査（独立オーディター）と修正

Sprint 3 を品質オーディターで監査 → **条件付き合格**。実装の問題2件を高速パスで修正済み：

1. `GapAnalysisService.finalizeJobRollupAndGapFlag`（rows<2 / medZ==null の早期確定パス）が
   生成元を記録していなかった → `AdviceSource.TEMPLATE_FALLBACK.name()` を明示記録するよう修正。
2. フロント `useJobStatusPolling.mergeJobStatusPreservingSummary` が `adviceSource` を引き継がず、
   PDF 生成中のポーリングでバッジが消える恐れ → `adviceSource: previous.adviceSource` を追加。

修正後 compile BUILD SUCCESS / 単体 19 件パス / フロント型クリーン（本件外の既存エラーのみ）。

## 関連する核

- 核③ SaaS グロース（Teaser）／核① WOW の裏返しの誠実さ（簡易分析モードの明示）

## 参照

- 仕様書: `.cursor/plans/2026-05-30-debate-driven-advice.md`
- 関連実装: `application/service/DebateAdviceGeneratorService.java`, `StrategyInsightService.java`, `BatchPersistenceService.findProjectAdviceContext`, `GapAnalysisService.runForJob`, `GeminiResultProcessor.processOutputJsonlAndUpsertResults`（line 162-167 削除）, `frontend/components/LoadingCharacter.tsx`, `frontend/pages/JobAnalysisPage.tsx`

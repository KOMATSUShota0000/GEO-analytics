# ADR: 複数クエリ自動生成とプラン別クエリ本数

- 日付: 2026-06-09
- ステータス: 採用
- 関連: CPO仕様 `.cursor/plans/2026-06-09-multi-query-generation.md`

## 背景

解析が1クエリのみで実行され、SoM・改Z'・AI認識が1点依存で統計的に脆弱だった
（`JobController` が `defaultInitialQuery` 1本のみを登録）。オーナー指摘
「クエリ数1では信頼性が無い」を受け、複数クエリ化する。

## 決定

1. **クエリ生成方式 = LLM生成**: 保存済みの事業概要・ターゲット・フォーカスを
   入力に、Gemini で多角的な日本語検索クエリを生成する（`JobQueryGenerationService`）。
   ルールベースは画一的でGEOの多角測定に不向きなため不採用。
2. **課金モデル = N本=Nチケット（現状維持）**: `keywordCount × DEPOSIT_PER_KEYWORD`
   の既存ロジックをそのまま使う。多クエリ=多コストが素直に課金へ反映され、
   限界利益率86%を崩さない。`realtimeBatchMax` で上限ガード。
3. **プラン別デフォルトN = Standard 3 / Pro 5 / Expert 10**: `SubscriptionPlan.defaultQueryCount()`
   に定数化し、調整を容易にする（`realtimeBatchMax` 10/50/50 以内）。
4. **堅牢性**: 生成の先頭は必ずブランド軸クエリ（ブランド名＋ドメイン）を確保し、
   LLM 生成失敗時はブランド軸1本にフォールバックして解析を止めない。
   生成自体はクレジット予約せず（前処理）、課金は解析本数（submitQueries）で行う。

## 検討した代替案

- **1解析=1チケット固定**: チケットを解析単位にしクエリ本数をプラン特典とする案。
  「1解析1チケット」原則には素直だが `DEPOSIT_PER_KEYWORD` 課金ロジックの改修が必要で
  実装が重い。今回はオーナー判断により N本=Nチケット（現状維持）を採用。
- **ルールベース生成**: コスト0だが画一的。GEOの多角測定の質が出ないため不採用。

## 既知のトレードオフ

- Standard は totalLimit=10。デフォルト3本だと月3解析程度になる。プラン設計上の
  既知の制約として受容（将来 totalLimit/PRD50 との整合を別途検討）。

## 影響

- 新規: `JobQueryGenerationService`・`GeneratedQueries`・`QueryGenerationOutputSchema`・`QueryGenerationPrompts`。
- 変更: `AiConfig`(bean追加)・`SubscriptionPlan`(defaultQueryCount)・`JobController`(実プラン解決＋N本投入)。
- DB スキーマ変更なし。既存ジョブへの影響なし（新規解析から複数クエリ）。

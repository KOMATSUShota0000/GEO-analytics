# ADR-024: AI自己認識エビデンス（スコア非算入の定性ステート）

## 日付
2026-06-04

## 状況
V13_GEO4AXIS の Sprint 3。SoM（Share of Model）は「生成AIにどれだけ言及されたか（量）」を測るが、「AIがそのブランドを**正しい実体として認識しているか（質）**」は測れていなかった。同名他社との取り違えやハルシネーション、そもそも認識されていない状態は、レポートの改善ストーリー（「第三者言及を増やせ→AIに正しく認識される」）を厚くする重要な定性情報である。

ただしこれを**スコアに算入すると SoM との二重計上**になる（言及されている＝ある程度認識されている、と相関するため）。また追加のLLM呼び出しはコスト（核④・限界利益率86%）を圧迫する。

## 決定
SoM測定で**既に取得済みのGemini応答**を再利用し（追加API呼び出しゼロ）、クエリ単位で `AiRecognitionState`（`RECOGNIZED_CORRECTLY` / `MISIDENTIFIED` / `UNKNOWN`）を導出して `audit_histories.ai_recognition_state` に永続化する。

- 判定は純粋ロジック `AiRecognitionClassifier.classify(brandMentioned, resolvedEntityLabel, canonicalBrand)`。
  - `brandMentioned=false` または 実体ラベル/基準が空 → `UNKNOWN`
  - 言及あり かつ 正規化後ラベルが正規ブランド名と一致（法人格・空白の揺れを吸収、十分長な包含も同一視）→ `RECOGNIZED_CORRECTLY`
  - 言及あり かつ 別実体に解決 → `MISIDENTIFIED`
- 入力シグナルは応答由来（言及有無）と `EntityNormalizer` の解決ラベル、顧客の正規ブランド名（`JobEntity.brandName`）のみ。
- 永続化は `JobPersistenceService.upsertAuditHistoryForJobQuery` 内で算出・セット。**スコア計算層（`SomScoreCalculator`/`GeoVisibilityCalculatorService`）には一切渡さない。**

## 理由
- **スコア非算入の構造的保証**: 算出を永続化層に閉じ込め、スコア計算の入力経路（`VerificationResponse`/`SyncVerificationResult`）に新フィールドを足さないことで、誤ってスコアへ混入する余地を物理的に断った。これは二重計上回避（受け入れ条件）を設計で担保する。
- **追加コストゼロ**: 既存の `brandMentioned`・`resolvedEntityLabel` を再利用するため、新たなLLM/外部API呼び出しが発生しない。
- **代替案との比較**:
  - (a) 新たにLLMへ「認識しているか」を問い合わせる案 → 追加コスト発生・核④に反するため却下。
  - (b) `VerificationResponse` に項目を足してアダプタで算出し通す案 → DTO2段（`VerificationResponse`/`SyncVerificationResult`）と全コンストラクタの改修が必要で波及大。かつスコア入力DTOに「スコア非算入の値」を載せること自体が誤混入リスクになるため却下。

## 結果
- レポート/フロント（Sprint 4）はクエリ単位の `ai_recognition_state` を集計し「AIによる認識状況」を定性表示できる。
- スコア（SoM・GEO Readiness）には一切影響しない。経時スナップショットの数値も不変。
- トレードオフ: 判定は応答1件ごとのヒューリスティックであり、`EntityNormalizer` の解決精度に依存する。`resolvedEntityLabel` が顧客名と表記揺れする稀なケースで `MISIDENTIFIED` 寄りに振れる可能性があるが、定性エビデンス（スコア非算入）のため影響は限定的。閾値・正規化規則は実データで調整余地あり。
- DBは `audit_histories` と `audit_histories_aud` に `ai_recognition_state VARCHAR(32)` を追加（既存行は NULL=未判定）。

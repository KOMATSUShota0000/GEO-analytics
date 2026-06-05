# ADR-026: AI認識状況のジョブ単位集約とレスポンス露出

## 日付
2026-06-05

## 状況
Sprint3 で `audit_histories.ai_recognition_state`（クエリ単位の AI 認識状態）を永続化したが、どの Web DTO にも露出しておらず、フロントのレポートで「AIは現在あなたをどう認識しているか」を表示できなかった（Sprint4 調査で判明）。レポートはジョブ単位の定性サマリと、クエリ別の状態の双方を必要とする。

## 決定
クエリ単位の状態を**ジョブ単位へ集約**し、per-query とジョブサマリの両方をレスポンスに露出する。**スコアには一切算入しない**（表示専用エビデンス）。

- per-query: `ResultDetailResponse` に `ai_recognition_state` を追加（`AuditHistoryEntity.getAiRecognitionState()` から充填）。
- 集約: 純粋ロジック `AiRecognitionAggregator.aggregate(Collection<AiRecognitionState>)` → 不変の `AiRecognitionSummary`（dominant ＋ recognized/misidentified/unknown/evaluated の件数）。
  - 代表ステート `dominant` の決定（**オーナー方針 2026-06-05 確定**）: **取り違えが1件でもあれば MISIDENTIFIED**（警告として表に出す）。取り違えが無く1件でも正しく認識されていれば RECOGNIZED_CORRECTLY。全て未認識・評価0件は UNKNOWN。null は件数から除外。
- ジョブサマリ: `JobAnalysisDetailResponse` に `ai_recognition_summary`（`AiRecognitionSummaryResponse`）を追加。`JobController` で audits の状態を集約して渡す。

## 理由
- **件数も保持**することで、フロント（Sprint4b-2）は「大半で正しく認識／一部で取り違え」等のニュアンス表現ができ、単一ステートより情報量が多い。
- 集約を純粋ロジックに切り出し、`JobController` での組み立てとは独立に単体テスト可能にした。
- 集約は計算サービス（`SomScoreCalculator`/`GeoVisibilityCalculatorService`）に一切触れず、**スコア非算入を構造的に維持**（ADR-024 の方針を継続）。
- **「1件でも取り違えなら警告」を採用した理由（オーナー判断）**: GEOでは「AIに正しい実体として認識されること」が成果に直結するため、誤認識の見逃しを避け慎重側に倒す。多数決案（最多を代表）も検討したが、「多数が正しくても1件の誤認識を埋もれさせたくない」を優先。実数の偏りは内訳件数で別途可視化されるため、ニュアンスは保てる。

## 結果
- フロントは `ai_recognition_summary`（ジョブ全体）と各 `ai_recognition_state`（クエリ別）を使い、AI認識状況セクションを表示できる（Sprint4b-2）。
- スコア（SoM・GEO Readiness）には影響しない。
- 4a-1 とはファイルが分離しており（本サブは ResultDetailResponse/JobAnalysisDetailResponse/JobController＋新規集約）、独立してマージ可能。

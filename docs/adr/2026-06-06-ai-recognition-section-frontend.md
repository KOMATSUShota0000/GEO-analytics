# ADR-029: AI認識状況セクションのフロント表示（スコア別枠の定性エビデンス）

## 日付
2026-06-06

## 状況

Sprint3（ADR-024）でクエリ単位の `AiRecognitionState`（RECOGNIZED_CORRECTLY / MISIDENTIFIED / UNKNOWN）を永続化し、Sprint4a-2（ADR-026）でこれをジョブ単位に集約した `ai_recognition_summary`（dominant＋recognized/misidentified/unknown/evaluated の各件数）をジョブ解析レスポンスへ露出した。

しかしフロントはこのデータを一切表示していなかった。「AIがブランドを正しい実体として認識しているか（質）」という、SoM（量）では測れない強力な定性エビデンスがレポートに出ていない状態だった。

## 決定

ジョブ解析レポート（`JobAnalysisPage`）に、スコアカード直後の**別枠セクション**として AI認識状況を表示する新コンポーネント `AiRecognitionSection.tsx` を追加する。

- `types/analysis.ts` に `AiRecognitionState` 型・`AiRecognitionSummary` 型・`parseAiRecognitionSummary` を追加。`JobAnalysisDetail.aiRecognitionSummary` として `parseJobAnalysisDetail` で `ai_recognition_summary`（snake/camel両対応）を読む。
- `AiRecognitionSection` は `dominant` に応じて見出し・説明・アクセント色を切り替える:
  - RECOGNIZED_CORRECTLY（緑）: 「正しく認識」維持・強化の文言。
  - MISIDENTIFIED（橙）: 「取り違え」是正の改善導線（構造化データ・第三者言及）。
  - UNKNOWN（灰）: 「未認識」権威・エンティティ認知の強化が最優先の導線。
- 件数チップ（正しく認識 / 取り違え / 未認識 + 評価クエリ数）を表示。dominantが肯定でも `misidentifiedCount>0` 等のときは件数でニュアンス補足文を出す。
- `evaluatedCount<=0`（未評価・旧データ過渡期）はセクションごと非表示。

## 理由

- **スコア非算入の厳守**：本セクションはスコア経路に一切触れず、`ai_recognition_summary` を表示するだけ。SoM（量）との二重計上を構造的に回避する（enum定義・ADR-024の方針どおり）。
- **集約規則の確定（B案）に追随**：「取り違え1件でも MISIDENTIFIED を dominant にする」警告優先の集約はBE側（ADR-026）で確定済み。フロントは dominant を素直に主表示しつつ、件数でニュアンスを足すことで過度な警告と情報量を両立する。
- **色はステートのセマンティクス（緑/橙/灰）**でブランドカラーに依存させない。スコアではなく「状態」の説明のため、ホワイトラベルでも意味が壊れない。スコアカード（ブランドカラー使用）とは役割が異なる。
- スコア直後に置くことで「点数（量）→ 認識の質」という読み筋を作り、改善ストーリーを補強する。

## 結果

- レポートに AI認識の質エビデンスが表示され、MISIDENTIFIED/UNKNOWN 時は改善アクションへの動機づけになる。
- `vite build` 緑。新規型エラーなし。
- スコープはレポート本体（`JobAnalysisPage`）。`PublicDemoPage` への反映は任意の後続（デモは `GeoScoreBreakdown` 単体利用のため）。
- per-query の `ai_recognition_state`（`ResultDetailResponse` に露出済み）は本セクションでは未使用。将来クエリ別ドリルダウンを出す場合の素地として残る。

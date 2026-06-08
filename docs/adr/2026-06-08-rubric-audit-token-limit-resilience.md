# ADR: ルーブリック監査のトークン上限引き上げと出力破損リトライ

- 日付: 2026-06-08
- ステータス: 採用
- 関連: CPO仕様 `.cursor/plans/2026-06-08-rubric-audit-score-zero-fix.md`

## 背景

オーナー実機テスト（ジョブ `3491be0a-...` / ブランド「おにぎりこんが」/ https://fbih.jp/）で、
GEO Readiness Score の3軸（コンテンツ50 / 構造20 / 権威30）が全て0になる事象が発覚した。
SoM スコア（12.0点）は正常だったため、ルーブリック監査系統に限定した障害と判明。

ログ解析の結果、ルーブリック監査LLM `geminiRubricAuditModel` の `maxOutputTokens` が
4096 に設定されており、10項目＋日本語 evidence の出力がトークン上限に達して
4項目目（`items[3]`）の途中で打ち切られ、JSON が破損していた。
`RubricAuditService` の `objectMapper.readValue` が `Unexpected end-of-input` で例外を投げ、
`AiRubricAuditService.auditOneDomain` で例外が伝播。LLM監査のみならず、
LLM不要のシステム監査（構造軸）まで巻き添えで失われ、当該ドメインの監査結果が
ゼロ件となって全軸0表示に至った。

## 決定

1. **トークン上限の統一**: `geminiRubricAuditModel` の `maxOutputTokens` を 4096 → 8192 に。
   debate / remediation / domain-analysis 等の他の構造化出力モデルと整合させ、
   10項目＋evidence の出力に十分な余裕を持たせる。
2. **メソッド内リトライ**: `RubricAuditService.auditWithCreditReservation` に最大2試行の
   リトライを導入。`chat → readValue → validate` のいずれかが失敗したら1回だけ再試行する。
   `@CreditReservation` はメソッド単位のため、リトライしてもチケット消費は1回に保たれ、
   限界利益率86%を侵さない。各試行で Gemini の `finishReason` を WARN ログに残し、
   `MAX_TOKENS` 等の原因を即座に特定できるようにする。

## 検討した代替案

- **JSON 修復（部分パース）**: 破損 JSON を後段で補修して取り込む案。実装が複雑で
  誤った監査値を生むリスクがあり却下。上限引き上げ＋リトライで根治する方が単純で安全。
- **LLM失敗時にシステム監査だけ残す部分劣化**: 構造軸だけ出してコンテンツ/権威軸を
  欠落させる案。欠損レポートを B2B 顧客に出すのは誤った0点と同程度に有害なため不採用。

## 既知の残課題（次スプリント）

- リトライ後もパース失敗が残った場合、現状は「0点のままジョブ COMPLETED」となり、
  顧客に誤レポートを提示しうる。「LLM軸が取得不能ならジョブをエラー/再試行へ倒す」
  可視化はジョブステータス・UI へ波及するため本スプリントでは扱わず、別途検討する。

## 影響

- 変更ファイル: `AiConfig.java`、`RubricAuditService.java`。
- API・DB スキーマ・フロントエンドへの変更なし。後方互換。

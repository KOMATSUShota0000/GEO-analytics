# ADR-025: スコア内訳DTOのV13新3軸露出（追加方式）

## 日付
2026-06-05

## 状況
Sprint1〜3 でスコア計算は V13_GEO4AXIS（コンテンツ50/技術20/権威30）へ移行したが、フロント向けの `web/dto/ScoreBreakdown` は旧3フィールド（`ai_audit_total`/`meo_total`/`machine_readability_total`/`final_score`）のみで、`computeBreakdown` は内部でV13計算して `final_score` は正しく出すものの**権威小計・圧縮後の技術素地を捨てていた**。結果、レポートの総合点はV13・内訳バーは旧モデルで合計が合わない不整合が生じていた（Sprint4 調査で判明）。

## 決定
`web/dto/ScoreBreakdown` に V13 3軸の内訳フィールドを**追加（additive）**し、`computeBreakdown` で充填する。旧フィールドはフロント移行（Sprint4b）完了まで後方互換のため残す。

- 追加フィールド: `content_total`(0-50) / `technical_total`(0-20) / `authority_total`(0-30) / `authority_third_party_core`(0-20) / `authority_local_meo_sub`(0-10) / `authority_wikipedia_kg_bonus`(0-10, 当面0) / `calculation_version`。
- 小計の算出は `GeoVisibilityCalculatorService` に公開ヘルパー `technicalSubScore` / `authorityThirdPartyCore` / `authorityLocalMeoSub` を新設し、`calculateFinalGeoScore`・`combineAuthority` をそれらの単一ソースへリファクタ（挙動不変）。`computeBreakdown` も同ヘルパーを使う。
- `calculation_version` は最新 `AuditHistoryEntity.getCalculationVersion()` から供給。

## 理由
- **追加方式**にすることで、本DTOを main に入れてもフロントは旧フィールドで動き続け、main が壊れない（段階移行）。
- 小計ロジックを計算サービスの公開ヘルパーに集約することで、`computeBreakdown` と `combineAuthority`/`calculateFinalGeoScore` の**二重定義（マジックナンバー重複）を排除**。`content + technical + authority = final_score` の整合をテストで保証。
- 代替案（DTOを破壊的に置換）はフロントを同時改修するまで main が緑にならず、段階監査ができないため却下。

## 結果
- フロントは Sprint4b で新フィールドへ移行し、3軸内訳・権威内訳を正しく表示できる。
- 旧フィールドの撤去は 4b 完了後の cleanup で（Shadow Implementation 回避のため段階的に）。
- `authority_wikipedia_kg_bonus` は Sprint5 まで常時0（無くても減点しない方針）。
- 計算挙動・スコア値は不変（リファクタは等価）。既存テスト緑＋新規ユニット（technical圧縮・権威小計・総合点整合）を追加。

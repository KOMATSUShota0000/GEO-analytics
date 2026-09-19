# ADR-058: クロールした自社サイト本文を材料にする検証経路を撤去する

## 日付
2026-09-20

## 状況

ADR-039 で「AI 回答内での見え方を測る指標に自社サイト本文を混ぜるのは自作自演」と判断し、材料を実測の AI Overview へ切り替えた（ADR-045）。バッチ経路（ADR-046）、test-sync（ADR-057）と順に揃えた結果、**サイト本文を材料にする検証経路の呼び出し元が1つも無くなった**。

残っていたもの:

- `SyncVerificationService.verifyWithUrl`（3つの多重定義）
- `ConsultantPrompts.userTextBrandQueryWithWebsiteExtract` と `userBody` のサイト本文分岐
- `VerificationRequest` のクロール関連フィールド（`crawledContent` / `contentHash` / `domainTrustScore` / `technicalSeoEvidenceSummary`）
- `DomainTrustService.applyDomainPolicy`（クロール本文がある場合のみ意味を持つ）
- `GeoVisibilityCalculatorService.sourceWeightFromUrl`（#60 で SoM から外し、参照が消えていた）

## 決定

**これらをまとめて撤去する。**

## 理由

`.cursorrules` 10節は「Shadow Implementation 禁止／孤立したコードは許容しない」と定める。呼ばれない経路を残すと、次の担当者が「配線漏れでは」と判断して繋ぎ直す危険がある。実際 #81 では、死蔵していた集約が SoM の列へ別の指標を書き込む実装のまま残っていた。

## 残したもの

| 対象 | 理由 |
|---|---|
| `LlmWebsiteTextClip` | ルーブリック監査（自社サイトの評価）が使用中 |
| `GeoVisibilityCalculatorService.CALCULATION_VERSION`（V13_GEO4AXIS） | 準備度スナップショットの版名として使用中 |
| `DomainTrustService` のドメイン規則キャッシュ | RAG のドメイン規則で使用中 |

## 結果

- 検証の材料は「実測の AI Overview」か「材料なし（推定）」の2択になり、分岐が1箇所（`ConsultantPrompts.userBody`）に収まった。
- `VerificationRequest` が6項目に減り、何を材料に測ったのかがコードから読めるようになった。
- `analysisTextLength` は「解析に使った材料の長さ」として AI Overview 本文の長さを指すようになった（従来はクロール本文の長さ）。

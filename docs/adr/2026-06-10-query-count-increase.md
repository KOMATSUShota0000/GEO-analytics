# ADR: デフォルトクエリ本数の引き上げ（Pro 5→10 / Expert 10→30）

- 日付: 2026-06-10
- ステータス: 採用
- 関連: ADR `2026-06-09-multi-query-generation.md`、CPO仕様 `.cursor/plans/2026-06-09-multi-query-generation.md`

## 背景

複数クエリ自動生成（2026-06-09）でデフォルト本数を Standard 3 / Pro 5 / Expert 10 とした。
その後オーナーから「クエリ数を20〜30に増やせないか。費用的にきついか」という要望。
SoM 等の信頼性は多クエリほど高まるため、特に上位プランで本数を増やしたい。

## 決定

`SubscriptionPlan.defaultQueryCount()` を以下に引き上げる。

| プラン | 変更前 | 変更後 | 据え置き理由／効果 |
|--------|------:|------:|------|
| Standard | 3 | **3（据え置き）** | `totalLimit`=10・`realtimeBatchMax`=10 の制約上、20〜30は物理的に不可 |
| Pro | 5 | **10** | `realtimeBatchMax`=50 以内。`totalLimit`=500 で月約50解析 |
| Expert | 10 | **30** | `realtimeBatchMax`=50 以内。`totalLimit`=2000 で月約66解析 |

## 理由

- **限界利益率86%は不変**: 本数 N に対し SerpAPI と Gemini のコストが比例して増えるが、課金も N本=Nチケットで比例する（ADR `2026-06-09-multi-query-generation` の課金モデル）。1チケットあたりのコスト構造は変わらないため、本数を増やしても利益率は崩れない。
- **絶対コストは小さい**: 1クエリ ≈ SerpAPI＋Gemini Flash で約1.5円。Expert 30クエリでも1解析あたり約45円で、月額¥59,800から見て誤差。
- **技術上限内**: いずれも `realtimeBatchMax`（Pro/Expert=50）以内。新規テストで `defaultQueryCount <= realtimeBatchMax` を不変条件として担保する。
- **アップセル設計**: 上位プランほど多角的に測れる構図を強め、Pro→Expert の動機付けを高める（核③）。

## トレードオフ

- 1解析あたりのチケット消費が増える（Expert は30チケット/解析）。`totalLimit` に対する月間解析回数は Pro 約50・Expert 約66 で十分実用的と判断。
- Standard は据え置き。Standard で多クエリを求めるユーザーは Pro/Expert への誘導対象とする。
- 既知の課題として Standard の `totalLimit`=10 と過去PRD（50枚）の不整合は別途整合を要する（multi-query ADR でも言及済み）。

## 影響

- 変更: `SubscriptionPlan.defaultQueryCount()`（定数のみ）。
- 追加: `SubscriptionPlanTest`（値の固定＋`realtimeBatchMax` 不変条件）。
- DB・課金ロジック・UIの変更なし。次回解析から新しいデフォルト本数が適用される。

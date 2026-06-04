# ADR-019: GEO Readiness の業種別ウェイト化

## 日付
2026-06-03

## 状況

GEO Readiness Score（`GeoVisibilityCalculatorService.calculateFinalGeoScore`）は業種に関係なく **AI監査(0-50) + MEOトラスト(0-25) + 機械可読性(0-25) = 100** の固定加算だった。

MEO（Google Places の口コミ・評価）は地域店舗には重要だが、全国展開のBtoBサービス（CORPORATE_SERVICE）やオンライン完結サービス（ONLINE_SERVICE）には構造的に無関係で `meo_review_count` が常に null/0 になる。結果、これらの業種では **25点の枠が恒久的にゼロ**となり、最高でも75点しか出ない「天井25%減」問題が起きていた。代理店がBtoB/ECクライアントへ提案する際にスコアが不当に低く見える。

## 決定

`calculateFinalGeoScore` に業種 `CompetitorExtractionMode` を引数追加し、配点を切り替える。

| 業種 | AI監査 | MEO | 機械可読性 | 合計 |
|------|-------|-----|-----------|------|
| LOCAL_STORE | 0-50（×1.0） | 0-25（×1.0） | 0-25（×1.0） | 100 |
| CORPORATE_SERVICE / ONLINE_SERVICE | 0-60（×1.2） | 除外 | 0-40（×1.6） | 100 |

- 非地域業種は MEO を評価から外し、その配点を AI監査(×1.2) と機械可読性(×1.6) へ再配分。天井100は維持。
- 呼び出し元2箇所で業種を `JobEntity.getCompetitorExtractionMode()` から取得して渡す:
  - `RubricGapAnalysisService.aggregateAndPersistFinalScore`（最終スコア永続化）— `JobRepository` を新規注入。
  - `JobPersistenceService.computeBreakdown`（解析結果表示の内訳算出）。
- フロント `GeoScoreBreakdown` に `industryMode` prop を追加。非地域業種では **MEO行を非表示**にし、AI監査の上限を60・機械可読性の上限を40として表示（バーが新配点を正しく反映）。再配分係数はバックエンドと一致させ、表示値も重み付け後の値にする。

## 理由

- MEO は地域SEO（来店誘導）の指標であり、GEO（LLM回答内可視性）の文脈では地域業態にしか効かない。業種で配点を変えるのが本質的に正しい。
- 既存の LOCAL_STORE は配点を厳密に維持し、過去スナップショット（`geo_asset_snapshots`）との連続性を保った。
- AI監査と機械可読性は全業種でLLM評価に直結するため、MEO除外分の再配分先として妥当。
- 代替案「MEO行を残して0表示」は天井問題が残るため却下。「業種無視で3軸固定」は提案説得力を損なうため却下。

## 結果

- CORPORATE/ONLINE のジョブで MEO=0 でも AI・機械の再配分により最高100点に到達可能になった。
- スコアの**数値の意味が業種で変わる**。同一サイトでも業種設定が違えば最終スコアが変わりうる点を運用上認識する必要がある。
- `calculateFinalGeoScore` のシグネチャを変更（3引数→4引数）。Shadow実装を残さず全呼び出し元を更新。

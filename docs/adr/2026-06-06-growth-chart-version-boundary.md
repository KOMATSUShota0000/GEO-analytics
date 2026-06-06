# ADR-030: 経時グラフへのV12/V13採点版切替点の明示

## 日付
2026-06-06

## 状況

Sprint4a-3（ADR-027）で `geo_asset_snapshots` をV13スコアへ統一し、各スナップショットに `calculation_version`（V13_GEO4AXIS / 旧データはV12_PWIM）を記録、`AssetSnapshotChartPoint` DTO へ `calculation_version` を露出した。

しかしフロントの成長軌跡グラフ（`GrowthTrajectoryChart`）はこの版情報を受け取らず、V12時代とV13時代のスコアを**一本の折れ線で地続きに**描いていた。採点基準が変わった境界が見えないため、版切替に伴うスコアの段差を「実際の改善/悪化」と誤読する危険があった。

## 決定

経時グラフに採点ロジックの切替点を `ReferenceLine`（縦の点線）で明示する。

- `useProjectAssetSnapshots` の `AssetSnapshotChartPoint` 型に `calculationVersion: string | null` を追加し、スナップショットAPI応答（camel変換後 `calculationVersion`、未提供時null）からパースする。
- `GrowthTrajectoryChart` に `findVersionBoundaries` を追加。隣接スナップショットで `calculationVersion` が変化した点を境界として抽出する。**両端が既知（非null）かつ異なるときのみ**境界化し、版不明（null）の旧データはノイズ回避のため境界に含めない。
- 各境界に Recharts `ReferenceLine`（`strokeDasharray`・slate色の点線）を引き、`{短縮版名} へ`（V13_GEO4AXIS→V13 等）のラベルを付す。
- 境界が1つ以上ある場合のみ、グラフ上部に「縦線は採点ロジックの切替点。前後で基準が異なり、線をまたぐ増減はそのまま比較できない」旨の注記を表示する。

## 理由

- **誤読防止が主目的**：版切替の段差は基準変更によるもので、施策成果ではない。境界を可視化し注記を添えることで、提案時の誤った因果説明を防ぐ。
- 線色はブランドカラーではなく**中立のslate点線**にした。折れ線（ブランドカラー）が主役で、境界はあくまで補助情報。ホワイトラベルでも破綻しない。
- nullを境界に含めない判断：版記録のない古いスナップショットとの境界まで描くと過剰な縦線が出る。既知版どうしの遷移（V12→V13）に限定して意味のある1本に絞る。
- 短縮版名（V13/V12）はラベルの可読性のため。完全版名（V13_GEO4AXIS）は長くグラフ上で潰れる。

## 結果

- V12→V13 の切替を跨ぐプロジェクトで、段差の理由が一目で分かるようになった。
- 単一版しか持たないプロジェクト（新規・全期間V13）では縦線も注記も出ず、見た目はこれまでどおり。
- `tsc --noEmit` は本変更起因エラーなし（型に追加したフィールドのパース漏れをtscが検出→修正済み）。`vite build` 緑。
- per-query/analytics-summary 側のtrend（`TrendDataPoint`）には版が無く、本対応はスナップショット由来の成長軌跡グラフに限定。必要なら別途同様の対応を検討（当面スコープ外）。

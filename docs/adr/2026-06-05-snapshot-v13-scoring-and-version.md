# ADR-027: 経時スナップショットのV13スコア移行と calculation_version 記録

## 日付
2026-06-05

## 状況
経時推移グラフ（GrowthTrajectoryChart）のデータ源 `geo_asset_snapshots` には次の問題があった（Sprint4 調査で判明）。

- スナップショットの `readinessScore` は旧 `DefaultScoringService`(50/25/25) で算出され、しかも `JobAuditMetricsExtractor` が平均SoMしか供給していなかった（MEO/機械可読性=0）。実質「平均SoMだけの退化スコア」で、per-job レポートの V13 GEO Readiness とは別モデル・別物だった。
- `calculation_version` を保持しておらず、V12→V13 の切替点をグラフに描けなかった。

このまま version 列だけ足すと、中身が旧モデルのスナップショットを「V13」と誤ラベルすることになる（オーナー判断で本物のV13化を選択）。

## 決定
スナップショットのスコアを **per-job レポートと同一の V13 GEO Readiness（`ScoreBreakdown.finalScore`）に統一**し、`calculation_version` を記録・露出する。

- `GeoAssetSnapshotService.createSnapshot` は `JobPersistenceService` の breakdown（Sprint4a-1 で V13 finalScore＋version を保持）を再利用して `readinessScore` と `calculationVersion` を設定する。スコアの単一ソース化。
- 旧 `ScoringService` / `DefaultScoringService` / `domain.scoring.ScoreBreakdown` / `WeightedScoreInput` と `ScoringServiceTest` は唯一の利用者が本サービスだったため**削除**（`domain/scoring/` パッケージ全撤去・Shadow Implementation 撤去）。
- `geo_asset_snapshots.calculation_version`(VARCHAR32) を追加（V132）。既存行は NULL。
- 露出: `AssetSnapshotChartPoint.calculation_version` を追加。`GeoAssetSnapshotQueryService` は NULL を旧モデル `V12_PWIM` として返す。
- `localTrustCount` は従来どおり `JobAuditMetricsExtractor` から供給（スコア軸と独立の表示用カウント）。

## 理由
- **バージョン併記移行の本来の姿**: 過去データは旧モデルのまま凍結（NULL→V12_PWIM 表示）、新規ジョブから V13_GEO4AXIS を記録。グラフは切替点を `calculation_version` の変化で検出でき、Sprint4b-3 の V12/V13 マーカーが意味を持つ。
- **単一ソース化**: snapshot と report の GEO Readiness が一致し、「グラフと詳細で数字が違う」混乱を解消。退化した avgSoM スコアを排除。
- `ScopedValue` ベースのテナントスコープはネスト再束縛が安全なため、スナップショット生成（テナントスコープ内）からの `JobPersistenceService` 呼び出し（内部で executeWithTenant）も問題ない。

## 結果
- 経時グラフが V13 の正しいスコアで描かれ、過去（V12_PWIM）との切替点が明示できる。
- 旧スコアリングの死蔵コードを排除（`domain/scoring/` 配下5ファイル削除＝パッケージ全撤去）。
- 過去スナップショットの数値は変更しない（再計算しない・バージョン併記）。
- 申し送り: `JobAuditMetricsExtractor` の SoM系出力は localTrustCount 以外が未使用化。将来 localTrustCount の意味付け（現状0）と合わせて整理余地あり（軽微）。

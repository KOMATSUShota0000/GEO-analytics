# ADR-054: 死蔵していたスコア集約を撤去する

## 日付
2026-09-19

## 状況

最終スコアを集約して永続化する経路が2つあった（#81）。

| 経路 | 呼び出し元 | 書き込み先 |
|---|---|---|
| `JobPersistenceService`（`ScoreBreakdown` 組み立て）→ `GeoAssetSnapshotService` | **本番稼働中** | `geo_asset_snapshots.readiness_score` |
| `RubricGapAnalysisService.aggregateAndPersistFinalScore` | **呼び出し元ゼロ** | `audit_histories.som_score` / `gbvs_normalized_score` |

調査の結果、両者は**同じ値を計算していた**（同じ既定業種、丸めの桁だけ違う）。つまり計算としての重複であり、稼働しているのは前者だけだった。

### 死蔵側には潜在的なバグがあった

`aggregateAndPersistFinalScore` は **GEO 準備度の合計（コンテンツ＋技術＋権威、0〜100）を `som_score` と `gbvs_normalized_score` へ書き込む**。この2列は「AI 回答内での可視性（SoM）」を保持する列であり、まったく別の指標。もし配線されていたら、SoM が準備度の値で上書きされていた。

## 決定

**`aggregateAndPersistFinalScore` を撤去する。** 併せて、それだけが使っていた補助メソッドと依存（`AuditHistoryRepository` / `JobRepository`）も落とす。

## 理由

- 稼働している経路が別にあり、同じ値を出している。残す理由がない（`.cursorrules` 10節「孤立したコードは許容しない」）。
- 書き込み先が誤っているため、将来誰かが「配線漏れだ」と判断して繋ぐと**スコアが壊れる**。残しておくこと自体がリスク。

## 結果

- `RubricGapAnalysisService` はギャップ抽出（`identifyGaps`、#75 で改善タスク生成へ配線済み）だけを担う。
- スコアの永続化経路は1つになった。
- 挙動は変わらない（元から呼ばれていなかったため）。

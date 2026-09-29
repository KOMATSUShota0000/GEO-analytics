# ADR-091: 成長の推移を解析結果の画面に直接載せ、戦略ダッシュボードの画面を消す

## 日付

2026-09-30

## 状況

- 「成長の推移」は、解析結果の画面の「プロジェクト」欄のリンクから開く別画面（戦略ダッシュボード `/projects/:projectId/strategy`、`StrategyDashboardPage`）にあった（#180、ADR-084）
- その画面の中身は3つだけだった。見出し、オレンジの帯（感情アラート。解析結果の画面にも同じものがある）、「絶対評価」の2枚のグラフ（GEO Readiness の推移、ローカルトラスト蓄積）
- ローカルトラスト蓄積は、数字を作る `JobAuditMetricsExtractor.extract` がいつも 0 を返しており、平らな 0 の線を描くだけだった（#215 のコメントの 1）
- PDF は解析1回ぶんの印刷用画面（`ReportPrintPage`）から作るため、成長の推移は PDF に入らなかった（#215 のコメントの 3）
- オーナーの決定（2026-09-30、#224）:
  1. 成長の推移は、クリックして開く別画面ではなく、解析結果の画面に直接載せる
  2. 別画面と入口のリンクは消す
  3. 推移は「その解析の日まで」の90日を見せる
  4. ローカルトラスト蓄積はいったん隠す（作り直しは #220）

## 決定

### 1. 推移のグラフを、解析結果の画面の「GEO Readiness Score」の内訳の直後に置く

推移の数字は、内訳のカードと同じ `ScoreBreakdown.finalScore`（GEO Readiness）なので、隣に置いて話をつなげる。完了した解析で、期間内に点が1つ以上あるときだけ出す。見た目は結果画面のほかの節（白いカードに見出しと説明文）にそろえ、見出しは「成長の推移」にする。点が1つだけのときは、同じ屋号・ブランド名で解析を重ねると線になることを添える。

### 2. 期間は「その解析の日まで」の90日にする

期間の終わりは、解析結果の `createdAt`（時差を含む時刻）の最大を日本時間の日付に直した日。結果が無いときは今日。始まりはその90日前。決め方は `growthTrendRangeForJob`（`frontend/src/lib/growthTrendRange.ts`）の1か所に置き、PDF（#225）でも使う。

- 古い解析の画面・PDF に、そのあとの解析の点を混ぜないため。いつ開いても、いつ印刷しても中身が同じになる
- 解析結果の `auditDate` は `LocalDate.now()`（サーバーの既定のタイムゾーン）で記録され、推移の点（`GeoAssetSnapshotService`、日本時間）とずれることがあるので使わない

### 3. 解析が完了した直後は、推移の点を数回だけ取り直す

推移の点は、解析が完了になったあと、別の仮想スレッドで保存される（`GeoAssetSnapshotPipeline`）。完了した直後に開いた画面では、その解析の点がまだ無いことがある。期間の終わりの日の点が無ければ、3秒おきに最大4回だけ取り直す（`useGrowthTrend`）。

### 4. 戦略ダッシュボードの画面と、それだけが使っていたものを消す

同じグラフを2か所に置かないため。`StrategyDashboardPage`・ルート・入口のリンク「成長の推移」、`AbsoluteEvaluationSection`、`useLatestEmotionalAlert`（この画面のためにプロジェクトの最新の解析の感情アラートを読んでいた）、`bannerJobHint`（上のために、結果画面を開くたびに解析のIDをブラウザに書き込んでいた）、印刷用 CSS の `strategy-dashboard-print-root` を消す。`/projects/:projectId/strategy` を開くと、既存のルートの決まりでホームへ移る。

### 5. ローカルトラスト蓄積は描かないが、部品は残す

`TrustAccumulationChart` はどこからも使わなくなるが、#220 で「Google マップの口コミ件数」として作り直すときに使うので、ファイルは残す。

## 理由

- **結果画面に置く理由**: デモ動画や代理店の説明で、結果画面を見せるだけで「測って、直して、伸びた」までが伝わる。別画面へのリンクは見落とされやすい
- **別画面を消す理由**: 残すと、同じグラフの見た目や期間を2か所で直すことになり、ずれていく。別画面にしかなかった「公式提案書」「印刷日時」の見出しは、PDF（`ReportPrintPage` の表紙）が同じ役目を持つ
- **日付だけで区切る影響**: 推移の記録には時刻が無いので、同じ日にあとから行った解析の点は入る。日本時間の0時をまたいで終わった解析は、自分の点が翌日付になり、期間から外れることがある（まれなので受け入れる。直すなら、スナップショットに解析のIDを持たせる）

## 結果

| 区分 | ファイル |
|---|---|
| 新規 | `frontend/src/lib/growthTrendRange.ts`、`frontend/src/hooks/useGrowthTrend.ts` |
| 変更 | `frontend/src/pages/JobAnalysisPage.tsx`（推移の節を追加・入口のリンクを削除）、`frontend/src/components/strategy/GrowthTrajectoryChart.tsx`（結果画面の節の形に）、`frontend/src/App.tsx`、`frontend/src/index.css`、`README.md`、`docs/PDF_DOCKER_NOTES.md` |
| 削除 | `frontend/src/pages/StrategyDashboardPage.tsx`、`frontend/src/components/strategy/AbsoluteEvaluationSection.tsx`、`frontend/src/hooks/useLatestEmotionalAlert.ts`、`frontend/src/lib/bannerJobHint.ts` |

## 申し送り

- PDF に同じ節を載せる（#225）
- ローカルトラスト蓄積の作り直し（#220）、Places API を地域密着型だけで呼ぶ（#221）
- 上位プランへの誘導（#192）と SoM の推移（#177。保留中）は、置き場所が結果画面の中の「成長の推移」の節に変わった（両 issue にコメント済み）
- 公開デモ（`/demo`、ADR-090）は本番の結果画面と同じ部品・同じ順番で見せる方針だが、推移の節はまだ入れていない。デモは別のセッションの担当なので、入れるかどうかはデモの担当とオーナーで決める
- 利用者のブラウザには、`bannerJobHint` が書き込んでいた `geo.bannerJobHint.v1` が残るが、読む処理が無くなったので害はない

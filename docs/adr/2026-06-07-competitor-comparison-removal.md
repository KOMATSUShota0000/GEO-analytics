# ADR-031: 競合比較機能の廃止（GEO=絶対評価へ）— Sprint C1: フロント表示撤去

## 日付
2026-06-07

## 状況

GEOピボット（自社のGEO Readiness 3軸＝コンテンツ/技術/権威の絶対評価）が製品の中心になり、SEO時代の名残である「3競合を特定して比較表示する」機能が陳腐化した。競合抽出は合成競合（synthetic）でのパディングを含み、中身の伴わない「形だけ」になりがちで、オーナーから完全削除の方針が出た。

注意: `CompetitorExtractionMode` は名前に反し**業種分類（LOCAL_STORE/CORPORATE_SERVICE/ONLINE_SERVICE）**で、V13スコアの権威ローカルMEO小計のゲートに必須。また SerpAPI 基盤（`GeoCompetitorSearch*`）は権威軸（第三者言及）が共用する。これらは**残す**。

全体計画は `.cursor/plans/2026-06-07-competitor-removal-impact-analysis.md`（段階削除 C1〜C5）。

## 決定

段階削除の **Sprint C1＝フロント表示の撤去**を実施した。

- `AnalysisCharts.tsx`: SoV（Share of Voice）円グラフを完全除去し、スコア推移（AreaChart）のみに。`shareData` prop と関連 import を削除。
- `RelativeEvaluationSection.tsx` / `useRelativeBenchmark.ts`: ファイル削除。`StrategyDashboardPage` から使用箇所を除去（`AbsoluteEvaluationSection` は残す）。
- `JobAnalysisPage.tsx` / `ReportPrintPage.tsx`: `ProjectInfoBlock` 等の競合一覧表示、`competitorPair`/`chartShareData` の useMemo と関連 import、`AnalysisCharts` の `shareData` prop を除去。
- `PublicDemoPage.tsx`: `SAMPLE_SHARE` と `shareData` prop を除去。ローディング文言の「競合のShare of Voice…」を「第三者言及・権威シグナル…」へ。
- `types/analysis.ts`: `CompetitorShare` 型・`buildCompetitorShareData`・`resolveChartShareData`・`competitorLabelsFromProject` を削除。`AnalyticsSummaryNormalized.share` と share パースを除去（trend/subscriptionPlan は維持）。
- 組織ルール（`.company/CLAUDE.md`）の核2を「競合比較」から「自社GEO Readiness の絶対評価レポート」に改訂。

BE（競合抽出エンジン・集約・`job_competitor_scores` テーブル）は後続スプリント C2〜C5 で撤去する。本C1時点では BE は競合データを送信し続けるが、FE は単に表示しない（段階削除のため一時的な未使用は許容）。

## 理由

- 競合比較（特に合成競合）は GEO の絶対評価という製品方針に反し、説得力を損なう「形だけ」機能だった。
- 円グラフは流用せず完全削除（オーナー判断）。将来「複数AIモデルのスコア比率」表示が必要になれば、競合配線に依存せずゼロから新規実装する方が残骸が残らず健全。
- 表示（FE）を先に撤去することで、ユーザーに見える変化を最小リスクで先行リリースし、BE撤去を安全に後続化できる。

## 結果

- レポート/ダッシュボード/公開デモから競合比較表示が消え、スコア推移＋自社3軸＋エビデンス（今後）に集中した画面になった。
- `vite build` 緑、削除シンボルの残骸ゼロ（監査確認済み）。残すべき `resolveChartTrendData`/`GeoScoreBreakdown`/`AbsoluteEvaluationSection`/`CompetitorExtractionMode` は健在。
- 申し送り: BE側に C2（API/集約から競合除去）〜C5（抽出エンジン削除・DBテーブルDROP・リネーム）が残る。`PublicDemoPage` のマーケ文言（「競合比較」）はLPコンテンツ刷新で別途見直し。

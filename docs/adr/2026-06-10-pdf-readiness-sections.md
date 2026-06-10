# ADR-XXX: PDFレポートへ Tier診断・GEO Readiness Score・AIブランド認識を移植

## 日付
2026-06-10

## 状況
ブラウザ印刷方式のPDFレポート（`ReportPrintPage.tsx`）は、画面（`JobAnalysisPage.tsx`）で表示している主要セクションのうち以下3つが欠落していた。

1. 市場ポジション診断（Tier）
2. GEO Readiness Score（3軸スコアブレイクダウン＋エビデンス）
3. AIブランド認識状況

PDFは代理店がクライアントへ手渡すホワイトラベル成果物であり、絶対評価レポートの核（プロダクトの核②「実利」）がPDFに載らないことは説得力の致命的な欠落だった。あわせて、PDFの解析結果一覧でSoMスコアが `11.999999999999998` のように浮動小数点の生値で表示されるバグがあった。

## 決定
`ReportPrintPage.tsx` に、戦略診断セクションの直後・スコア推移チャートの手前へ、画面と同じ順序で3セクションを追加した。

- **市場ポジション診断（Tier）**: 既存 `TierDiagnosisCard` を再利用。SoM平均は画面と同じ `resolveAverageSomScore(resultRows, {}, false)` で算出し、完了済みかつ結果が空のときのみ 0 にフォールバック。配布物のためアップセル誘導は `isProPlan={true}` で抑止（コンポーネント側のアップセル枠 `pdf-no-print` と二重に保証）。
- **GEO Readiness Score**: 既存 `GeoScoreBreakdown` を再利用。`scoreBreakdown` が無いジョブでは丸ごと省略。
- **AIブランド認識状況**: 既存 `AiRecognitionSection` を再利用。評価件数0のジョブではコンポーネント側で非表示。
- **SoMスコアの桁丸め**: 解析結果一覧の表示を新ヘルパー `formatSomScore`（画面と同じく `gbvsNormalizedScore ?? somScore` を優先し小数第1位へ整形）に置換。

各セクションは既存PDFセクションと同じ改ページ抑制（`pdf-inside-avoid` / `breakInside: avoid`）でラップした。

## 理由
- PDFページは画面と**同じ** `/api/v1/jobs/{id}/analysis` を叩いており、3セクションが要するデータ（`scoreBreakdown`・`contentEvidence`・`technicalEvidence`・`aiRecognitionSummary`・各SoM）はすでに取得済み。`mergeJobAnalysisWithPdfContext` は浅いコピーでこれらを保持する。よって**新規API・新規LLM呼び出しは不要**で、1解析＝1チケットの原則（高利益率）に一切影響しない。
- 新規コンポーネントを作らず既存を再利用することで、画面とPDFの表示ロジック・世界観の一貫性を担保し、二重メンテナンスを避けた（ボーイスカウト・ルール／Shadow Implementation回避）。
- 代替案として「PDF専用の簡易版セクションを新規作成」も検討したが、画面との情報量・見た目の乖離を生むため却下した。

## 結果
- PDFに絶対評価レポートの核（Tier・Readiness・AI認識）が揃い、ホワイトラベル成果物としての価値が画面と同等になった。
- SoMスコアの生値表示バグが解消（`11.999999999999998` → `12.0`）。
- データが無いジョブでは各セクションが省略され、レイアウトは破綻しない。
- トレードオフ: PDFページが MUI ベースの重めのコンポーネント（`GeoScoreBreakdown` / `AiRecognitionSection`）を取り込むため、印刷時のレンダリング要素が増える。改ページ抑制で割れは防いだが、長尺ジョブでのページ数は増加する。
- 既存負債 `requestPdfReport`（未使用・TS6133）は本変更の対象外。PDF Sprint2（Playwright生成系の段階撤去）で撤去予定。

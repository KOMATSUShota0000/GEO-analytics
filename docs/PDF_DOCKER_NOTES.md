# PDFレポート出力に関するメモ（現状）

> ⚠️ 2026-05-20 更新（ADR-007）: 本ドキュメントは旧 Playwright サーバーサイド PDF 生成
> パイプラインを記述していたが、**その方式は廃止済み**であり実コードと矛盾していたため
> 現状に合わせて全面改訂した。

## 現在の方式: ブラウザ印刷（サーバーサイド PDF 生成なし）

- サーバー側の自動 PDF 生成は**廃止済み**。
  `JobPersistenceService.tryMarkPdfGeneratingAndPublish` は
  「サーバー側のPDF自動生成は廃止されました。ブラウザの印刷機能などをご利用ください。」を返す。
- PDF 出力は**フロントエンドのブラウザ印刷**で行う。
  解析結果の画面の「PDFとして保存」が印刷用の画面 `ReportPrintPage`（`/reports/print/:jobId`）を
  新しいタブで開き、印刷用 CSS（`pdf-avoid-break` / `pdf-no-print` 等）と描画完了マーカー
  `#pdf-ready-flag` を備えたその画面を、ブラウザの「印刷 → PDF として保存」でホワイトラベルの
  レポートとして出力する。
  （2026-09-30 追記: 以前はここに戦略ダッシュボード `StrategyDashboardPage` を挙げていたが、
  #224 で画面ごと削除し、成長の推移は解析結果の画面に移した。ADR-2026-06-10「PDF保存をブラウザ印刷方式に確定」も参照）

## 依存・インフラ要件

- **Playwright / ヘッドレス Chromium は不要**。`pom.xml` に Playwright 依存は無い。
  Docker イメージへ `playwright install --with-deps chromium` を入れる必要はない。
- 日本語フォントは**閲覧/印刷するクライアント側ブラウザ**の責務。サーバーイメージへの
  CJK フォント同梱は PDF 目的では不要。
- かつて必要とされた `app.pdf.base-url` / `APP_PDF_BASE_URL` はサーバー生成専用であり、
  現方式では不要。

## 旧方式（歴史的記録・現在は無効）

旧実装は Playwright ＋ ヘッドレス Chromium でサーバーサイド PDF を生成していた。
当時は `playwright install --with-deps chromium`・CJK フォント同梱・`APP_PDF_BASE_URL`
が必要だったが、いずれも現コードには存在しない。新たな環境構築でこれらを準備する必要はない。

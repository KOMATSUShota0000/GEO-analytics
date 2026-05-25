# 仕様書: PDF機能をブラウザ印刷フローに再配線（Sprint10.7）

- **作成**: CPO室 / 2026-05-21
- **関連**: Sprint9（サーバPDF廃止）／オーナー実機検証で「廃止メッセージ」が表示される事象

## 1. 背景・目的

Sprint9 でサーバ側 PDF 生成を廃止しブラウザ印刷方式に移行したが、UI 側の PDF ボタンが旧 API（`/pdf/request`）を叩き続けており、`JobPersistenceService:741` の「廃止メッセージ」がユーザーに露出している。
ブラウザ印刷向けの `/reports/print/:jobId`（`ReportPrintPage`）は既に完成しているため、**ボタン配線を新方式に繋ぎ直すだけ**で PDF 機能を生き返らせる。

## 2. 設計（How）

### 2.1 `JobAnalysisPage` 改修
- `onClick={requestPdfReport}` を `onClick={openPrintReport}` に変更
- `openPrintReport`: 新タブで `/reports/print/{jobId}?print=1&internal_token={token}` を `window.open` する
- ボタンラベル「PDFレポートを生成」→「PDFとして保存」に変更（ユーザーの認知整合）
- `pdfRequestInFlight` / `pdfRequestNotice` 等の死スタイル分岐はそのまま残す（後方互換・別Sprintで掃除）

### 2.2 `ReportPrintPage` 改修
- `URLSearchParams` から `print` パラメータを読む
- `pdfReadyFlag === true`（fonts+logo+data 全準備完了）になった時、`print === "1"` なら `window.print()` を自動発火
- 1回だけ発火するよう ref で多重起動を防ぐ
- ユーザーが印刷ダイアログでキャンセル/保存後、タブを閉じるのは任意

### 2.3 トークン
`VITE_PDF_INTERNAL_TOKEN`（dev フォールバック `dev-internal-token`）を URL に付与。既存 `tokenOk` チェック経路をそのまま流用＝認可ロジック不変。

## 3. 非機能・退行防止

- バックエンド・DB・JSON 契約・他ページに変更なし
- 旧 API `/pdf/request` `/pdf/download` は **削除しない**（後方互換）。ただしフロントが呼ばない＝ユーザーには見えなくなる
- `ReportPrintPage` 既存の Playwright 想定挙動（`#pdf-ready-flag` の DOM マーカー）はそのまま維持＝将来サーバPDF 復活させる場合の経路も殺さない

## 4. 受け入れ基準（オーディター監査項目）

1. `cd frontend && npm run build` 成功
2. `JobAnalysisPage` の PDF ボタン onClick が `requestPdfReport` でなくなっていること
3. `ReportPrintPage` で `?print=1` クエリ時に `window.print()` が一度だけ呼ばれること（多重起動防止）
4. 旧 API 関数 `requestPdfReport` は残置でも OK（dead code 検出はスコープ外）
5. ADR 不要（既存パターンの UI 再配線のみ）

## 5. スコープ外

- B: 競合URL未表示の改善（synthetic placeholder の UI 表現は別 Sprint）
- C: Gemini 空 contents の 400 ガード（ログ汚染は別 Sprint）
- 旧 PDF API エンドポイント完全削除

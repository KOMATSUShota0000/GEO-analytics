# PDFレポート（Playwright）Docker/Linux向けメモ

LinuxコンテナでPDF生成を安定させるには、次を想定してください。

1. Playwrightのシステム依存関係  
   ビルドまたはランタイムで `playwright install --with-deps chromium`（またはプロジェクト方針に合わせたブラウザ）を実行し、ヘッドレスChromiumに必要なライブラリを揃える。

2. 日本語フォント  
   文字化けを防ぐため、Noto Sans CJK JP（`fonts-noto-cjk` 等）や Noto Sans / Noto Serif の日本語パッケージをイメージに含める。フロントの日本語UIを正しく描画するにはブラウザが参照できるフォントパスが必要。

3. `app.pdf.base-url`  
   コンテナ間・同一ホスト内でフロント（Viteビルドの静的配信またはリバースプロキシ）が到達可能なURLを環境変数 `APP_PDF_BASE_URL` 等で指定する。

# 本番デプロイ環境変数ガイド

geo-analytics を本番運用する際に設定すべき環境変数の一覧と、未設定時の挙動。

## 必須・推奨環境変数

| 環境変数 | 用途 | 未設定時の挙動 | 必須度 |
|----------|------|----------------|--------|
| `SERPAPI_API_KEY` | 競合エビデンスの実取得（`app.serpapi.api-key`） | **プレースホルダに降格**。4人ペルソナ議論・相対評価の品質が低下。起動時に WARN ログ（`SerpApiKeyStartupCheck`） | 本番必須 |
| `GOOGLE_PLACES_API_KEY` | ローカルストア競合の Places 取得（`app.places.api-key`） | Places 由来競合が取得されず合成参照モデルに降格 | ローカル業種で必須 |
| Gemini APIキー | メイン AI（`gemini-2.5-flash`） | AI 解析全般が不可。**本番前に application.yml ハードコードを環境変数化必須** | 必須 |
| JWT secret（`app.security.jwt.secret`） | 認証トークン署名 | 起動不可/不安全 | 必須 |
| `app.security.jwt.cookie-secure=true` | リフレッシュ Cookie の Secure 属性 | HTTPS 本番では true 必須 | 必須 |

## 起動時の可視化

`SerpApiKeyStartupCheck`（`ApplicationRunner`）が起動時に SERPAPI キー状態をログ出力する。

- 未設定: `WARN  SERPAPI_API_KEY が未設定です。…プレースホルダに降格します…`
- 設定済: `INFO  SERPAPI key configured (masked=abcd****). 競合エビデンス実取得が有効です。`

→ 本番デプロイ後はこのログを確認し、競合データ取得が有効かを必ず検証すること。

## PDF レポート

サーバーサイド PDF 生成は廃止済み。PDF 関連の環境変数（旧 `APP_PDF_BASE_URL` 等）・
Playwright ランタイムは不要。詳細は `PDF_DOCKER_NOTES.md` を参照。

## 参考

- ADR-001: SerpAPI 競合エビデンス本接続（フォールバック設計）
- ADR-007: 運用整備（SERPAPI 起動警告 / PDF・Playwright 懸念の事実確認）

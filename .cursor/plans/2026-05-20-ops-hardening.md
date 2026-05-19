# 仕様書: 運用整備（SERPAPI 起動警告 / PDF・Playwright 懸念の事実確認）

- **作成**: CPO室 / 2026-05-20
- **対象スプリント**: Sprint9（dev-phase-log フェーズ9）
- **関連課題**: dev-status 懸念点#7（PDF/Playwright）・インフラメモ（SERPAPI 本番キー）

## 1. 背景・スコープの正直な切り分け（What / Why）

### SERPAPI（コードで対処可能）
`app.serpapi.api-key: ${SERPAPI_API_KEY:}` は未設定時 空文字。`GeoCompetitorSearchAdapter`
は呼び出し時に `IllegalStateException` → 上位がプレースホルダ降格。**本番で誰も気づかず
競合データ無しで運用される事故リスク**。→ 起動時 WARN ログで可視化する。

### PDF/Playwright（コード調査の結果＝懸念は陳腐化）
コード精査による事実：
- `pom.xml` に Playwright 依存は**存在しない**
- `JobPersistenceService.tryMarkPdfGeneratingAndPublish` は
  「サーバー側のPDF自動生成は廃止されました。ブラウザの印刷機能をご利用ください」を返す
- PDF はフロント `StrategyDashboardPage` の印刷CSS＋`pdf-ready-flag` による**ブラウザ印刷方式**

→ 「PDF生成に Playwright 依存」という懸念#7は**実体としてもう存在しない**。
本番コンテナでの Playwright 検証は不要（依存が消滅しているため）。
ただし `docs/PDF_DOCKER_NOTES.md` が旧 Playwright 前提のまま＝**コードと矛盾**しており、
これが「まだ Playwright 依存がある」という誤認の温床。事実に合わせて是正する。

> 注: 秘書は本番コンテナを直接検証できない。しかし「依存が削除済み」という
> コード由来の検証可能な事実により、コンテナ検証の必要性そのものが消える。

## 2. 設計（How）

### 2.1 SERPAPI 起動警告
`infrastructure.bootstrap.SerpApiKeyStartupCheck implements ApplicationRunner`
（`RagDomainRuleSeeder` のパターンを踏襲）：
- `AppProperties.getSerpapi().getApiKey()` が null/blank なら **WARN** ログ
- メッセージにプレースホルダ降格の影響と対処（`SERPAPI_API_KEY` 環境変数設定）を明記
- キー有効時は INFO 1行（マスク表示・先頭4文字のみ）
- 副作用なし・例外を投げない（起動を止めない）

### 2.2 PDF ドキュメント是正
`docs/PDF_DOCKER_NOTES.md` を**現状（ブラウザ印刷方式・サーバーPDF廃止）に書き換え**。
旧 Playwright 手順は「廃止済み（歴史的記録）」として明示。誤認防止。

### 2.3 デプロイ手順
`docs/DEPLOYMENT_ENV.md`（新規）に本番必須環境変数を集約：
`SERPAPI_API_KEY` / `GOOGLE_PLACES_API_KEY` / Gemini キー / JWT secret 等の一覧と未設定時挙動。

## 3. 受け入れ基準（オーディター監査項目）

1. `./mvnw clean test` 全件 PASS（既存退行なし）
2. `SerpApiKeyStartupCheckTest`：blank→WARN、有効→マスクINFO、null安全、例外を投げない
3. `pom.xml` に Playwright 依存が無いことの再確認（grep ゼロ）
4. `docs/PDF_DOCKER_NOTES.md` がコード現状（サーバーPDF廃止・ブラウザ印刷）と整合
5. `docs/DEPLOYMENT_ENV.md` に SERPAPI_API_KEY を含む本番環境変数が記載
6. アーキ規約遵守（起動チェックが例外で起動阻害しない・ThreadLocal等不使用）
7. ADR 作成（PDF懸念#7を「コード上消滅済み」と確定した根拠を記録）

## 4. スコープ外（正直な明示）

- 本番コンテナでの実地スモークテスト（秘書の実行環境外。かつ Playwright 依存削除済みのため不要）
- 実 `SERPAPI_API_KEY` の本番投入そのもの（オーナーのデプロイ作業／秘密情報）

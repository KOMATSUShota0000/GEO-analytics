# ADR-007: 運用整備（SERPAPI 起動警告 / PDF・Playwright 懸念の事実確認）

## 日付

2026-05-20

## 状況

2点の運用課題が残っていた。

1. **SERPAPI**: `app.serpapi.api-key: ${SERPAPI_API_KEY:}` は未設定時 空文字。
   `GeoCompetitorSearchAdapter` が呼び出し時に例外 → 上位がプレースホルダ降格する設計
   （ADR-001）だが、**未設定であることが運用者に何も通知されず**、本番で競合データ無しの
   まま気づかず運用される事故リスクがあった。
2. **PDF/Playwright（dev-status 懸念点#7）**: 「PDF 生成に Playwright 依存」が
   未解決懸念として残っていた。

## 決定

### SERPAPI
- `SerpApiKeyStartupCheck`（`ApplicationRunner`）を新設。起動時にキー状態をログ化：
  未設定 → WARN（影響と対処を明記）、設定済 → INFO（先頭4文字マスク）
- 例外は投げず起動を阻害しない（プレースホルダ降格は既存の正しい挙動）
- `docs/DEPLOYMENT_ENV.md` を新設し本番必須環境変数を集約

### PDF/Playwright
- コード精査により懸念#7は**実体として既に消滅している**ことを確定：
  - `pom.xml` に Playwright 依存が存在しない
  - `JobPersistenceService.tryMarkPdfGeneratingAndPublish` が
    「サーバー側のPDF自動生成は廃止されました」を返す
  - PDF はフロント `StrategyDashboardPage` の印刷CSS＋`#pdf-ready-flag` による
    ブラウザ印刷方式
- 旧 Playwright 前提のまま放置されコードと矛盾していた `docs/PDF_DOCKER_NOTES.md`
  を現状（サーバーPDF廃止・ブラウザ印刷）に全面改訂

## 理由

- **可観測性**: 「静かに劣化する」設定ミスは本番事故の典型。起動時 WARN は最小コストで
  最大の事故防止効果
- **誤魔化さない／正直な検証**: 秘書は本番コンテナを直接スモークテストできない。
  しかし「Playwright 依存はコード上削除済み」という**検証可能なコード事実**により、
  コンテナ検証の必要性そのものが消える。憶測の「検証済み」を主張せず、依存消滅という
  事実で懸念を確定的に解消する
- **ドキュメント整合**: コードと矛盾するドキュメントは「まだ Playwright 依存がある」
  という誤認の温床。事実に合わせる是正は将来の判断ミスを防ぐ

## 結果

- 本番起動ログで SERPAPI キー設定有無が即座に判別可能に
- 本番必須環境変数が `DEPLOYMENT_ENV.md` に一元化
- 懸念#7（PDF/Playwright）はコード上消滅済みとして確定。`PDF_DOCKER_NOTES.md` は
  現状と整合
- スコープ外（正直な明示）: 本番コンテナ実地スモークテスト（実行環境外かつ依存削除済みで不要）、
  実 `SERPAPI_API_KEY` の本番投入（オーナーのデプロイ作業・秘密情報）

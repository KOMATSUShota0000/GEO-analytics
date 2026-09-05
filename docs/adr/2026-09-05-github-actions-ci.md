# ADR: GitHub Actions による CI の導入

- 日付: 2026-09-05
- ステータス: 採用
- 関連: `docs/DEVELOPMENT_SETUP.md`、ADR `2026-08-30-cross-platform-dev-toolchain.md`

## 背景

リポジトリにワークフロー定義が一度も存在せず、PR のマージ可否を機械的に検証する仕組みが無かった。230件のテストは揃っているが、実行はローカルの手作業に依存しており、実行忘れや環境差による見落としを防げない。

## 決定

`.github/workflows/ci.yml` を追加し、`main` への push と全 PR で以下を実行する。

| ジョブ | 内容 |
|--------|------|
| `backend` | JDK 25（Temurin）で `./mvnw -B clean test`。230件 |
| `frontend` | Node 22 で `npm ci` → `npm run build` |

## 理由

- **秘密情報を登録しない**: `.env` を外した状態で `RlsIntegrationTest`（Testcontainers + Flyway）と `CacheConfigTest`（Springコンテキスト起動）が通ることを事前に実測した。`application-test.yml` と `application-rls-it.yml` がダミー値を持つため、CI 側に API キーや DB パスワードを預ける必要がない。攻撃面を増やさずに済む。
- **Docker のセットアップを書かない**: `ubuntu-latest` には Docker が標準搭載されており、Testcontainers はそのまま動作する。明示的な起動処理は不要で、動作確認のみ行う。
- **2ジョブに分ける**: バックエンドとフロントエンドは依存関係が無いため並列実行できる。片方が落ちてももう片方の結果が得られ、原因の切り分けが速い。
- **`concurrency` で古い実行を打ち切る**: 同一ブランチへ連続 push した際、不要になった実行が課金枠を消費し続けるのを防ぐ。
- **`permissions: contents: read`**: 既定の write 権限は本ワークフローに不要。最小権限とする。
- **失敗時のみ surefire レポートを保存**: 成功時のアーティファクトは容量を食うだけで価値が薄い。

## トレードオフ

- Sudachi 辞書（208MB）は `clean` で `target/` が消えるため毎回ダウンロードされる。キャッシュ可能だが、`clean` の前にキャッシュを復元する必要があり構成が複雑になるため、初版では見送る。
- `npm run dbdoc:diff` による dbdoc の再生成漏れ検知は含めない。稼働中の PostgreSQL に Flyway を適用した状態が前提となり、別途 service コンテナとマイグレーション実行の配線が要る。別ADRで扱う。
- `actions/setup-java` が Temurin 25 を解決できることに依存する。解決できない場合は初回実行で明示的に失敗するため、サイレントな劣化は起きない。

## 影響

- 追加: `.github/workflows/ci.yml`。
- アプリケーションコード・DBスキーマへの影響なし。
- 以降、PR には2つのチェックが表示される。ブランチ保護ルールで必須化するかは別途判断する。

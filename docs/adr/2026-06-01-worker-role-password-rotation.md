# ADR-014: ワーカーDBロールパスワードの env 注入化（GitHub公開準備）

## 日付
2026-06-01

## 状況

GitHub 公開準備の一環で、コードベースから弱いシークレット文字列を排除する作業中、
`application.yml` / `application-dev.yml` の弱いデフォルトパスワード（`${API_WORKER_PASSWORD:api_worker_pass}` 等）
を必須化（デフォルト削除）した。しかしその過程で、より深刻な問題を発見した。

Flyway マイグレーション `V3__setup_roles_and_grants.sql` が、`api_worker` / `batch_worker`
ロールを**弱い固定パスワード**（`api_worker_pass` / `batch_worker_pass`）で `CREATE ROLE` / `ALTER ROLE`
していた。これにより:

1. 弱いパスワード文字列が公開リポのマイグレーションに残り続ける（衛生・セキュリティ）。
2. 新規環境で Flyway を流すとロール側が弱い固定値で作られ、`application.yml` を必須化しても
   env に強い値を入れた瞬間にパスワード不一致で接続失敗する（機能の片手落ち）。

2026-05-31 のシークレット棚卸しで既存ローカルDBのロールは手動 `ALTER ROLE` でローテート済みだが、
マイグレーションファイル自体は弱い値のままだった。

## 決定

V3 は改変せず、新規マイグレーション `V129__rotate_worker_role_passwords.sql` を追加し、
ワーカーロールのパスワードのみを**環境変数由来の値**へ `ALTER ROLE ... WITH PASSWORD` する。
値は Flyway placeholder（`spring.flyway.placeholders.*`）経由でアプリ環境変数
（`API_WORKER_PASSWORD` / `BATCH_WORKER_PASSWORD`）から注入する。

- `application.yml`（base）に `spring.flyway.placeholders` を追加し、env を必須（デフォルト無し）で橋渡し。
  dev / prod プロファイルは base を継承する。
- `application-rls-it.yml`（PostgreSQL・flyway有効）は placeholders をテストロールの値
  （`api_worker_pass` 等）で上書きし、V129 実行後も結合テストの datasource 接続が通るようにする。
- `application-test.yml`（H2・flyway無効）は placeholders をダミー値で上書きし、main の
  `${ENV}` 解決が走らないようにする。
- `.env.example` の `API_WORKER_PASSWORD` / `BATCH_WORKER_PASSWORD` は空化し、強い値の生成を案内。

## 理由

**なぜ V3 を直接書き換えないか**: V3 はリリース済みで、SQL本文を変更すると Flyway の checksum が
変わり、既存環境（V128 まで適用済み）の `validate` が失敗して起動不能になる（prod は
`validate-on-migrate` 既定ON）。新規 V129 なら既存 checksum は不変で安全。

**なぜ placeholder か**: Flyway の checksum は placeholder 置換**前**のファイル本文で計算されるため、
環境ごとに値が違っても V129 の checksum は不変。env 値の変更がマイグレーション検証を壊さない。

**なぜ env 必須（デフォルト無し）か**: 弱い値で黙って起動する事故を防ぐため、未設定時は
プロパティ解決で fail-fast させる。既に必須化済みの `application-prod.yml` と一貫する。

**代替案**: ロール作成を Flyway から外しセットアップ手順書／スクリプトに分離する案
（手順書化）も検討したが、構築フローの一貫性が崩れ運用負荷が増えるため不採用。

## 結果

- 新規クリーン環境でも env 指定の強いパスワードでワーカーロールが設定される。
- 既存環境の V1〜V128 checksum は不変で、validate は失敗しない。
- env 未設定時は起動が明示的に失敗する（弱い値で動かない）。
- トレードオフ: Flyway placeholder をプロジェクトに初導入した。既存マイグレーションに
  `${` リテラルが無いことを確認済みのため、placeholder 置換有効化による既存への影響はない。
- 留意点: パスワードに単一引用符 `'` を含めると ALTER 文が破綻するため、`.env.example` で
  引用符を含まない生成方式（`openssl rand -base64` 等）を案内している。
- V3 のロール作成行の弱い固定値は履歴整合のため残るが、V129 で必ず上書きされる。
- テスト用（`init.sql` / `PostgresTestBase` / `PostgresSuperuserTestBase`）の `api_worker_pass`
  文字列は使い捨てテストDB用で実害が低いため本ADRのスコープ外（別途検討）。

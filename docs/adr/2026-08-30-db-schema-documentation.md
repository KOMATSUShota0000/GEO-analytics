# ADR: DBスキーマドキュメントの自動生成（tbls）とGUIクライアントの方針

- 日付: 2026-08-30
- ステータス: 採用
- 関連: ADR `2026-08-30-cross-platform-dev-toolchain.md`

## 背景

39テーブル・39 RLSポリシーに育ったスキーマの全体像を把握する手段が、Flyway マイグレーション37本を読むこと以外に存在しなかった。GUIクライアント（DBeaver / DataGrip）のER図は「その場で見る」用途には足りるが、手元だけの表示でリポジトリに残らず、レビューにも載らない。

## 決定

### 1. スキーマドキュメントは tbls で生成し、`dbdoc/` としてリポジトリに commit する

`.tbls.yml` と `scripts/dbdoc.mjs` を追加し、`npm run dbdoc` で再生成、`npm run dbdoc:diff` で稼働DBとの差分を検知する。ER図は Mermaid 形式で出力し、GitHub と VSCode の Markdown プレビューでそのまま描画させる。

### 2. Envers 由来の監査テーブル14本と `flyway_schema_history` を除外する

### 3. 日常のデータ閲覧・編集用GUIは DBeaver を標準とする

## 理由

- **Mermaid を選んだ理由**: 画像を生成すると差分がバイナリになりレビューできない。Mermaid ならテキスト差分として読め、レンダリングは GitHub / VSCode 側が行うため追加ツールが要らない。
- **`dbdoc/` を commit する理由**: スキーマ変更のレビュー時に、マイグレーションSQLと同じ PR 内で構造の変化を確認できる。`dbdoc:diff` を CI に載せれば、再生成忘れを機械的に検出できる。
- **Envers テーブルを除外した理由**: `projects_aud` 等14本と `revinfo` は Hibernate Envers の監査スキーマだが、pom に envers 依存が無く、コードにも `@Audited` が一件も存在しない。書き込まれることのない死んだテーブルであり、ER図の3分の1を占めて可読性を著しく損なうため。除外により図の対象は24テーブルになる。
- **tbls をコンテナで動かし DB と同じ Docker ネットワークに載せた理由**: ホスト側のポート公開方法（`127.0.0.1` バインド）に依存せず、コンテナ名の名前解決だけで到達できる。`--user` でホストのUID/GIDを渡し、生成物が root 所有になるのを防ぐ。
- **DBeaver を標準とした理由**: 無料でライセンスに縛られず、ER図・対応DBの広さで DataGrip に劣らない。IntelliJ Ultimate 内蔵のDBツールはSQLエディタとコード連携で優位だが有料ライセンス前提のため、標準にはしない。両者の併用を妨げない。

## トレードオフ

- `dbdoc/` の再生成を忘れると実スキーマと乖離する。`npm run dbdoc:diff` を CI に組み込むことで担保する（CI 設定自体は本ADRの範囲外）。
- 生成には稼働中の `geo-postgres` コンテナが必要。スキーマは Flyway が作るため、アプリを一度起動した後でないと生成できない。
- Envers テーブルを除外したことで、将来 Envers を正式採用する場合は `.tbls.yml` の見直しが必要になる。ただし現状のアーキテクチャ方針は監査をRLS側に寄せており、その可能性は低い。

## 影響

- 追加: `.tbls.yml`、`scripts/dbdoc.mjs`、`dbdoc/`（24テーブル分のMarkdown＋`schema.json`）。
- 変更: `package.json`（`dbdoc` / `dbdoc:diff` スクリプト）。
- アプリケーションコード・DBスキーマへの影響なし。生成処理は読み取り専用。

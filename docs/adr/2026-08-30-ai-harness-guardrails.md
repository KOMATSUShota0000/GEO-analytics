# ADR: AIハーネスのガードレール整備（権限denyの明文化 / CLAUDE.md の参照整合）

- 日付: 2026-08-30
- ステータス: 採用
- 関連: `CLAUDE.md`、`.claude/settings.json`、`.cursorrules`、`docs/adr/2026-08-30-cross-platform-dev-toolchain.md`

## 背景

AIコーディングエージェント（Claude Code）から見たリポジトリの状態を点検したところ、3つの問題が判明した。

1. **保護宣言が存在しない。** プロジェクト側の `.claude/` ディレクトリ自体が無く、`permissions.deny` が未設定だった。結果として、APIキーとJWT秘密鍵の実値を持つ `.env`（`600`）がAIから素通しで読め、適用済みFlywayマイグレーション V1〜V133 も編集可能なまま置かれていた。後者はチェックサム破壊によりアプリ起動不能を招く不可逆な事故カテゴリである。
2. **`CLAUDE.md` の指示が3箇所とも実行不能だった。**
   - 「実装完了後の必須手順」が `C:\cursor\company\.company\secretary\notes\dev-status.md` を開くよう指示していたが、当該ファイルは存在しない。WSL/Linux 環境では Windows パス自体が解決できず、6ステップ全体が毎回空振りしていた。
   - 「詳細は `.cursorrules` を参照」と書かれていたが、`.cursorrules` は Cursor 専用ファイルで Claude Code は自動読込しない。禁止事項の本体（ゼロアロケーション、Bucket4j + Semaphore、ScopedValue の Carrier チェーン等）がAIに一切届いておらず、6行の要約表のみが伝わっていた。
   - テストコマンドが `npm run build` と記載されていたが、ルート `package.json` に `build` スクリプトは存在しない（`frontend/package.json` 側にある）。
3. **探索コストの高いフラット構造。** `application/service` に67ファイルがサブディレクトリなしで直置きされており、ディレクトリで候補を絞れない。加えてルート直下に `mvn-test-tail-check.txt`（2026-05-09 のテスト実行ログ残骸）が残っていた。名前は設定ファイルに見えるが実体はJVM警告ログで、参照していた `GlobalScorePipelineThroughputTest` は既に削除済み。

## 決定

### 1. `.claude/settings.json` を新設し `permissions.deny` を宣言する

`.env` は読み取りごと禁止（`.env.example` を参照先とする）。`src/main/resources/db/migration/**` は `Edit` のみ禁止し、`Write` は許可する（新規マイグレーション追加を妨げないため）。`target/` と `node_modules/` は検索汚染の防止として `Read` を禁止する。

### 2. `CLAUDE.md` の参照整合を回復する

- 実行不能な「dev-status 更新」セクションを削除
- 冒頭に `@.cursorrules` を置き、禁止事項の全文を自動で読み込ませる
- 検証コマンドを `./mvnw clean test` と `cd frontend && npm run build` に修正

### 3. 「どこに何があるか」と「触ってはいけない場所」を `CLAUDE.md` に明記する

## 理由

- **`deny` とドキュメント両方に書いた理由**: `settings.json` は機械的に止めるが理由を伝えられない。「なぜ触ってはいけないか」を `CLAUDE.md` 側に併記することで、AIが迂回策（例: 既存マイグレーションを編集する代わりに新規追加する）を自力で選べる。
- **マイグレーションを `Edit` のみ禁止にした理由**: 全面禁止にするとスキーマ変更作業そのものが止まる。破壊的なのは既存ファイルの改変だけであり、新規追加は正常な運用フロー。
- **リネームではなくディレクトリ地図で解決した理由**: `application/service` の67ファイルは責務別サブパッケージへの再編が本筋だが、67ファイルの移動はインポート全書き換えを伴い、レビュー可能な単位を超える。命名規則（`Job*Service`、`*Prompts`、`*OutputSchema`、`*Calculator`）は既に一貫しているため、その規則を明文化するだけで探索コストは実用上解消できる。再編は別途扱う。

## トレードオフ

- `CLAUDE.md` が 77行から 112行に増える。ただし `@.cursorrules` の取り込みにより、実効的にAIへ渡る情報量はそれ以上に増加する。目安の200行には収まっている。
- `.env` の `Read` 禁止により、環境変数の実値を前提としたデバッグはAIが自力で行えなくなる。`.env.example` にキー名は揃っているため、値の確認が必要な場面ではオーナーが手動で提示する運用とする。

## 影響

- 追加: `.claude/settings.json`
- 変更: `CLAUDE.md`
- 削除: `mvn-test-tail-check.txt`（陳腐化したログ残骸。参照元テストは削除済み、内容の被参照なしを確認）
- アプリケーションコード・DB・課金ロジックへの影響なし。

## 環境ギャップの調査結果

診断の過程で「WSL に JDK / Node が無い」と一度誤認したが、これは調査側の誤りだった。実際には本日のセットアップで `~/.local/share/devtools/` に JDK 25.0.4.1 (Temurin) / Node 22.23.2 / Maven 3.9.6 が正しく導入されている。真因は以下の2点で、いずれも別個の問題である。

### 1. `~/.bashrc` の読み込み順（本ADRで対応済み）

devtools の `env.sh` を読み込む行が、Ubuntu 既定の非対話シェル早期 return よりも**後ろ**に置かれていた。

```bash
case $- in
    *i*) ;;
      *) return;;   # ← 非対話シェルはここで打ち切られ、以降が実行されない
esac
```

このため対話シェルでは動く一方、AIエージェント・npm スクリプト・CI といった非対話シェルからは PATH に載らず、`./mvnw` が `JAVA_HOME is not defined correctly` で停止していた。`~/.profile` にも同じ読み込みがあるがログインシェル専用のため補完にならない。

**対応**: devtools ブロックを早期 return より前へ移動した。`env.sh` 側に PATH 重複ガードがあるため、対話シェルでの二重登録は起きないことを確認済み。

### 2. Docker 未導入（要 sudo のため未実行）

`./mvnw clean test` は 230 件中 212 件成功、18 件が失敗する。失敗の真因は全件共通で以下だった。

```
Caused by: java.lang.IllegalStateException:
  Docker is required for PostgreSQL-backed tests (PostgresTestBase subclasses).
```

`PostgresTestBase` / `PostgresSuperuserTestBase` の派生テストが Testcontainers で PostgreSQL 17 を起動するため Docker が必須だが、WSL・Windows どちらにも Docker が存在しない。ユーザーが用意済みの `~/geo-analytics-db.sh`（開発用 PostgreSQL コンテナ管理）も同じく Docker を前提としている。

**対応**: `scripts/setup-docker-wsl.sh` を追加した。`sudo` にパスワードが必要でエージェントからは実行できないため、オーナーによる手動実行を前提とする。Docker Desktop ではなくネイティブの Docker Engine（Ubuntu 公式 `docker.io` 29.1.3）を選んだ理由は、WSL2 で systemd が有効なため追加のブリッジが不要で、Docker Desktop の商用ライセンス条件も回避できるため。

### 3. `.env` の `GEMINI_API_KEY` が空（オーナー対応事項）

`FLYWAY_PASSWORD`（32文字）と `JWT_SECRET`（128文字）は設定済みだが、`GEMINI_API_KEY` の値が空。README が必須と定める3キーのうち1つが欠けており、AI解析を伴う機能は実行時に失敗する。値の性質上、オーナーによる設定が必要。

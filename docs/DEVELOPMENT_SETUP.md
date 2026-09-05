# 開発環境セットアップ手順

別マシンで同じ開発環境を再現するための手順。WSL2（Ubuntu）を前提とする。
本番運用の環境変数は [`DEPLOYMENT_ENV.md`](./DEPLOYMENT_ENV.md) を参照。

検証済み環境: Windows 11 + WSL2 / Ubuntu 26.04 LTS / systemd 有効

---

## 導入されるもの

| 種別 | 対象 | 版 | 導入方法 |
|---|---|---|---|
| 言語 | Temurin JDK | 25 | `scripts/setup-toolchain.sh`（sudo 不要） |
| ビルド | Apache Maven | 3.9.6 | 同上 |
| フロント | Node.js | 22 | 同上 |
| コンテナ | Docker Engine | 29系 | `scripts/setup-docker-wsl.sh`（**要 sudo**） |
| DB | PostgreSQL | 17（コンテナ） | `scripts/db.sh up` |
| DBクライアント | psql | 18 | Docker導入時に同梱 |

JDK/Maven/Node は `~/.local/share/devtools/` に置く。ディストリのパッケージ版に引きずられず、
`pom.xml` と README が要求する版を正確に固定するため。

---

## 手順

### 1. root 権限が必要なものを入れる（最初に1回だけ）

```bash
sudo bash scripts/setup-docker-wsl.sh
```

Docker Engine の導入、サービス有効化、実行ユーザーの `docker` グループ追加までを行う。
`psql` や `unzip` も欲しい場合は追加で:

```bash
sudo apt-get install -y docker-compose-v2 postgresql-client unzip zip
```

**続けて WSL を再起動する。** Windows 側の PowerShell で:

```powershell
wsl --shutdown
```

> **新しいターミナルを開くだけでは反映されない。**
> WSL はインスタンス起動時にユーザーの補助グループを読み込んで保持するため、
> 同じインスタンスに繋ぐ限り古いままになる。`wsl` は Windows のコマンドなので、
> WSL の中から叩いても `command not found` になる点にも注意。
> なお Ubuntu 26.04 には `newgrp` / `sg` が存在しないため、これらでの代用はできない。

反映確認:

```bash
id -nG          # docker が含まれること
docker ps       # エラーが出ないこと
```

### 2. ツールチェーンを入れる

```bash
bash scripts/setup-toolchain.sh
```

`~/.local/share/devtools/env.sh` を作り、`~/.bashrc` と `~/.profile` の**両方**から読ませる。

> Ubuntu の `.bashrc` は非対話シェルで早期 `return` するため、`.bashrc` だけに書くと
> スクリプト実行時（`bash -lc` など）に PATH が通らない。両方に入れる必要がある。

新しいシェルを開いて確認:

```bash
java -version   # 25.x
mvn -v          # 3.9.6
node -v         # v22.x
```

### 3. `.env` を用意する

```bash
cp .env.example .env
```

以下はローカル用にランダム生成して埋める。

```bash
# 生成例
openssl rand -hex 16   # FLYWAY_PASSWORD / DB_PASSWORD（同じ値にする）
openssl rand -hex 16   # API_WORKER_PASSWORD
openssl rand -hex 16   # BATCH_WORKER_PASSWORD
openssl rand -hex 64   # JWT_SECRET
```

外部APIキーは各自で取得する。必須度が異なる。

| キー | 起動 | 未設定時の挙動 |
|---|---|---|
| `GEMINI_API_KEY` | **必須** | **起動不可**。`geminiPromptInjectionGuardModel` の Bean 生成で `apiKey cannot be null or blank` |
| `SERPAPI_API_KEY` | 不要 | 競合エビデンスが空リストに縮退。4ペルソナ議論の質が下がる（起動時 WARN） |
| `GOOGLE_PLACES_API_KEY` | 不要 | Places 検索を呼んだときだけ `IllegalStateException` |
| `STRIPE_*` | 不要 | 決済フローが使えない |

> Gemini だけ起動を止めるのは、ビルダー内で即検証しているため。
> Spring は起動時に全 Bean を生成するので、コンストラクタ内の検証は実質「起動時チェック」になる。
> SerpAPI / Places は値を保持するだけで、検証を使用時のメソッドに置いているため起動は通る。

### 4. データベースを起動する

```bash
bash scripts/db.sh up
```

`postgres:17-alpine` を `geo-net` ネットワーク上に立て、`127.0.0.1:5432` で公開する。
パスワードは `.env` の `FLYWAY_PASSWORD` を自動で読む。`--restart unless-stopped` 付きなので
WSL や Docker デーモンを再起動しても自動復帰する。

| サブコマンド | 用途 |
|---|---|
| `bash scripts/db.sh up` | 起動（無ければ作成） |
| `bash scripts/db.sh down` | 停止（データはボリューム `geo-pgdata` に残る） |
| `bash scripts/db.sh psql` | コンテナ内の psql に入る |
| `bash scripts/db.sh status` | 状態確認 |

**スキーマはこの時点ではまだ空。** Flyway はアプリ起動時に走るので、次のステップで作られる。

---

## 動作確認

```bash
./mvnw clean test        # 230件すべて成功すること
cd frontend && npm ci && npm run build
cd .. && npm ci
npm run dev              # Vite(5173) と Spring Boot(8080) を同時起動
```

`npm run dev` の初回起動時に Flyway が37本のマイグレーションを流し、
39テーブル・39 RLSポリシー・`api_worker` / `batch_worker` ロールを作る。
続いて `DataSeeder` が初期ワークスペースとユーザーを作成する。

> **統合テストに `scripts/db.sh` のコンテナは不要。**
> `PostgresTestBase` / `PostgresSuperuserTestBase` の派生テストは Testcontainers が
> 使い捨てコンテナ（`postgres:16-alpine` / `postgres:17-alpine`）を自前で起動し、
> ランダムポートを `@DynamicPropertySource` で注入する。Docker さえ動いていれば通る。
> `scripts/db.sh` のコンテナが要るのは**アプリを実際に起動するとき**だけ。

---

## DBを見る

### DBeaver（データの閲覧・編集・SQL）

```powershell
winget install DBeaver.DBeaver.Community
```

| 項目 | 値 |
|---|---|
| Host / Port | `localhost` / `5432` |
| Database | `geo_analytics` |
| Username | **`postgres`** |
| Password | `.env` の `FLYWAY_PASSWORD` |

> **`api_worker` では接続しないこと。** RLS ポリシーが全て効き、
> `app.current_org_id` が未設定のセッションからは1行も返らないため、
> 全テーブルが空に見える。DBが壊れたと誤認しやすい。

WSL2 は Windows の localhost を転送するので、Windows 側の DBeaver からそのまま繋がる。

### tbls（スキーマ構造のドキュメント）

```bash
npm run dbdoc        # dbdoc/ を再生成
npm run dbdoc:diff   # 稼働DBと dbdoc/ の差分検知（CI向け）
```

`dbdoc/README.md` に全体の Mermaid ER図、各 `dbdoc/public.*.md` にテーブル単位の
定義と周辺ER図が入る。VSCode なら `Ctrl+Shift+V` のプレビューで描画される。
Envers 由来の死んだ監査テーブル14本と `flyway_schema_history` は `.tbls.yml` で除外済み。
詳細は ADR `2026-08-30-db-schema-documentation.md`。

**スキーマを変更したら `npm run dbdoc` を実行し、生成差分を同じ PR に含めること。**

---

## ハマりどころ

| 症状 | 原因と対処 |
|---|---|
| `docker ps` が permission denied | グループ未反映。`wsl --shutdown` してから開き直す。新しいターミナルを開くだけでは効かない |
| `wsl: command not found` | `wsl` は Windows 側のコマンド。WSL の中ではなく PowerShell で実行する |
| スクリプト実行時だけ `java: command not found` | `.bashrc` にしか PATH を書いていない。`.profile` にも必要（`scripts/setup-toolchain.sh` は両方に入れる） |
| 起動時に `apiKey cannot be null or blank` | `.env` の `GEMINI_API_KEY` が空。必須 |
| DBeaver で全テーブルが空 | `api_worker` で接続している。`postgres` で繋ぎ直す |
| DBに繋がらない | コンテナが停止している。`bash scripts/db.sh up` |
| テーブルはあるのに行が無い | Flyway はスキーマだけを作る。データは `DataSeeder`（アプリ起動時）と実際の操作で入る |
| `./mvnw` が動かない | Maven Wrapper は only-script 型。`unzip` が無い環境では `.tar.gz` に自動フォールバックするので追加導入は不要 |

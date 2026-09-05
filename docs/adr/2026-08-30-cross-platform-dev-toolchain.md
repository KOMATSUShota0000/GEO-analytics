# ADR: 開発ツールチェーンのクロスプラットフォーム化（Maven Wrapper 正規化 / npm スクリプトの OS 依存排除）

- 日付: 2026-08-30
- ステータス: 採用
- 関連: `README.md`（ローカルでの動かし方）、`CLAUDE.md`（作業前の必須手順 / テスト）

## 背景

WSL2（Ubuntu 26.04）上にクリーンな開発環境を構築した際、ビルド系が Windows 固有の前提に依存しており起動できないことが判明した。

1. `mvnw.cmd` が公式の Maven Wrapper ではなく、`MAVEN_HOME=C:\Users\komas\.m2\wrapper\dists\...` を直書きした手書きスタブだった。`.mvn/wrapper/maven-wrapper.properties` は存在するが参照されておらず、特定マシンの特定パスでしか動作しない。Unix 版 `mvnw` は同梱すらされていなかったため、`CLAUDE.md` と `README.md` が指定する `./mvnw clean test` が Linux/macOS で実行不能だった。
2. ルート `package.json` の `concurrently` が `^x.x.x` と指定されていた。npm は `x` をワイルドカードとして解釈するためエラーにはならないが、実質 `*` として最新メジャー（現時点で 10.0.5）へ解決される。`package-lock.json` が固定する 9.2.1 と乖離し、`npm ci` と `npm install` で別メジャーが入る。
3. `dev:safe` / `dev:fast` が `.\mvnw.cmd` を直接呼んでおり、cmd.exe 以外のシェルでは解決できない。

## 決定

### 1. Maven Wrapper を公式版で再生成

`maven-wrapper-plugin:3.3.2` の `only-script` タイプで `mvnw` / `mvnw.cmd` / `maven-wrapper.properties` を生成し直す。Maven 配布版は従来どおり 3.9.6 に固定する。

### 2. `concurrently` を lockfile と同じ `^9.2.1` に固定

### 3. npm スクリプトから OS 依存表記を排除

`scripts/mvnw.mjs` を新設し、`.\mvnw.cmd` の直呼びを `node scripts/mvnw.mjs` に置き換える。

## 理由

- **`only-script` タイプを選んだ理由**: `maven-wrapper.jar` を必要とせず、`.gitignore` 済みバイナリへの依存が消える。`unzip` が無い環境では自動的に `.tar.gz` にフォールバックするため、最小構成の Linux でも追加インストール無しで動く。
- **Node ランチャを挟んだ理由**: npm スクリプトのシェルは Windows が cmd.exe、Unix が sh で、両者を満たす単一表記が存在しない。cmd.exe は `./mvnw` を解釈できず、sh はカレントディレクトリを PATH 探索しないため `mvnw` を解決できない。`concurrently` を使う以上 Node は必須なので、差分吸収を Node 側に寄せるのが依存追加ゼロで済む。
- **`^9.2.1` に揃えた理由**: `npm ci` が実際に導入している版であり、lockfile との整合が取れる。ワイルドカードのままだと将来のメジャー更新を無警告で取り込む。

## トレードオフ

- `mvnw.cmd` が手書き 5 行から公式版 154 行に置き換わる。可読性は下がるが、これは Apache 公式配布物であり保守対象外とする。
- 初回の `./mvnw` 実行時に Maven 3.9.6 本体（約9MB）を `~/.m2/wrapper/dists/` へダウンロードする。これはラッパー本来の設計どおりの挙動。
- `scripts/` ディレクトリが新設される。ビルド補助スクリプトの置き場として今後も利用する。

## 影響

- 変更: `mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`、`package.json`、`package-lock.json`（`name` の不整合 `geo-analytics` → `geo-analytics-project` も併せて解消）。
- 追加: `mvnw`（実行権限付き）、`scripts/mvnw.mjs`。
- アプリケーションコード・DB・課金ロジックへの影響なし。
- 検証: WSL2 上で `./mvnw -v`、`npm ci`、`npm install`、`npm run dev:fast`（Vite 起動と Spring Boot のコンパイル完了まで）、`mvn test`（Docker 不要の 212 件）を確認。

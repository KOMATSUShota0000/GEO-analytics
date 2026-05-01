---
name: SSRF防衛バリデーター計画
overview: フェーズ1.5.1・第2回として、ネットワークアクセスなしで URL 宛先を検証する `SsrfValidator` の設計方針を、制約（やる／やらない）とロジックの具体策に分けて整理する。実コードは書かない。
todos:
  - id: align-requirements
    content: "アーキテクト承認: 制約・検証順・IPv4/IPv6/localhost 範囲・例外メッセージ言語を確定"
    status: pending
  - id: review-existing-ssrf
    content: 既存 SsrfValidator と計画の差分レビュー（userInfo 拒否・::ffff・fe80 等のポリシー合意）
    status: pending
  - id: tests-matrix
    content: 境界値中心のユニットテスト一覧（許可URL/拒否URL）をテストクラスで網羅
    status: pending
isProject: false
---

# SSRF 防衛バリデーター（第2回）— 開発計画と制約の復唱

## 1. 制約の確認

### やること（今回のスコープ）

- **純粋なバリデーション専用クラス**として、入力 URL 文字列を **DNS 名前解決・HTTP・HTML パースなし**に判定する。
- Spring の **`@Component`** としてコンテナ登録し、**`public void validate(String targetUrl)`** を唯一の公開 API とする（呼び出し側はフェッチ前に必ず実行する前提）。
- 不正検知時はドメイン層の **[`ScrapingException`](geo-analytics/src/main/java/com/geo/analytics/domain/exception/ScrapingException.java)** を、**理由が分かるメッセージ**（必要に応じて原因例外を `cause` として）付きでスローする。
- 検証は要件どおり **次の順序**で実施する:
  1. `null` / 空・_blank_
  2. **構文**: `java.net.URI` でパース可能か（`URISyntaxException` は `ScrapingException` にラップ）
  3. **スキーム**: `http` / `https` のみ（大文字小文字は無視）
  4. **ホスト**: 必須。**字面**上、`localhost`、IPv4 のプライベート／ループバック／リンクローカル相当、IPv6 ループバック等をブロック（下記ロジック設計）

### 絶対にやらないこと

- **Jsoup / HttpClient 等による実通信**、リダイレクト追従、コンテンツ取得。
- **`InetAddress.getByName` 等による名前解決**や、いかなる **外部ネットワーク I/O**（事前 DNS は SSRF 対策として採用しない方針を明示）。
- **HTML パース・Sanitize**（別コンポーネントの責務）。

### 既存コードベースとの関係（参考）

- [`geo-analytics/src/main/java/com/geo/analytics/infrastructure/security/SsrfValidator.java`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/security/SsrfValidator.java) に、上記方針に沿った実装が **すでに存在**し、[`JsoupUrlContentFetcherAdapter`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/adapter/JsoupUrlContentFetcherAdapter.java) から利用されている。今回の「計画」は **要件の承認用テンプレート**としての復唱であり、実装フェーズでは「この計画と diff を突き合わせレビューする／不足があれば追補する」位置づけになる。

---

## 2. ロジック設計（具体的方針）

### 2.1 パースとスキーム（手順 1〜3）

- **`new URI(targetUrl)`** で統一パース。失敗は構文エラーとして `ScrapingException`。
- **`getScheme()`** が `http` / `https` のみ。それ以外（`file`, `ftp`, `jar`, 相対 URL 相当で scheme 欠落など）は拒否。
- **`getHost()`** が null／空なら拒否（レジストリ駆動名・オパークな入力の早期排除）。
- **補強（推奨）**: **`getUserInfo() != null`**（`user:pass@host`）は SSRF／認証情報漏えいリスクのため拒否。要件に無いが実装済みの防御として計画に含めてよい。

### 2.2 ホスト字面の正規化（DNS なし前提）

- **大文字小文字**: ホスト比較は **`Locale.ROOT` で lower-case**。
- **ホモグルフィ対策（文字列のみ）**: 全角 `．` `：` を ASCII `.` `:` に置換してから判定（`new URI` 後の `getHost()` 文字列に対し、**解決はしない**）。
- **ホスト文字集合**: 検証対象を **`[a-z0-9.:\-\[\]]` 程度に制限**し、制御文字や不可視文字・国際化ドメインの生 Unicode 等は **「不正なホスト形式」として拒否**（Java の `URI` が許した奇妙な表現の再絞り込み）。※ IDN を許可する場合は別 Poicy（今回の要件外）。

### 2.3 IPv4 リテラル（手順 4 の一部）

- ホストが **ドット区切りの十進のみ**と判定できた場合:
  - **正規表現**で「各オクテット 0〜255」の形にマッチするか検証。曖昧な先頭ゼロのみ許す／拒むはポリシーだが、**数値範囲で最終判定**する方式が安全（現行実装はパターン + `parseInt`）。
- **ブロック範囲（オクテットベースの分岐）**:
  - `127.0.0.0/8`
  - `10.0.0.0/8`
  - `192.168.0.0/16`
  - `172.16.0.0`〜`172.31.0.0`
  - `169.254.0.0/16`
  - ループバック／未指定の典型として **`0.0.0.0` も拒否**（要件外だが SSRF でよく問題になるため推奨）。

### 2.4 IPv6 リテラル（手順 4）

- **`URI.getHost()`** が **`[` `]` で囲まれた IPv6** を返す場合、**外側の括弧を剥がし**内側文字列で解析。
- **埋め込み IPv4**（`::ffff:192.0.2.1` や `2001:db8::192.0.2.1` 形式）があれば **末尾 IPv4 部分を抽出**し、上記 **IPv4 ルールに委譲**。
- **純粋 IPv6** は、**`::` は高々 1 回**など RFC 5952 風に制限しつつ、**8 個の 16-bit 整数列に展開**。パース不能は `ScrapingException`（「不正なホスト／IP 形式」）。
- **ブロック判断（展開後のベクタ）**:
  - 全ゼロ（`::`）は **`0.0.0.0` 相当として拒否**。
  - **`::1` / 最終ワードが 1 で先行がすべて 0** のループバックを拒否（`[::1]` や `0:0:0:0:0:0:0:1` を包含）。
  - 要件に明示がないが、**リンクローカル `fe80::/10`**（先頭 16-bit が `0xfe80`〜`0xfebf`）も **字面ブロック**してよい（現行実装と整合）。

### 2.5 ドメイン名のみのホスト

- 上記 **IPv4 / IPv6 リテラル・`localhost`** に当てはまらない **通常のラベル**（例: `example.com`）は **このバリデータでは許可**する。名前解決しないため **`internal.corp` がプライベート IP に解決される**ケースは防げない—必要なら **別フェーズでプロキシ／許可リスト／フェッチ後のソケット先検証**などを検討する旨を制約として文書化する。

### 2.6 例外メッセージ方針

- メッセージは **英固定**／**日本語固定**のどちらかにプロジェクト標準を合わせる（現状は英語メッセージが多い）。アーキテクト承認時に統一方針を確定するとよい。

---

## 3. 承認後の実作業イメージ（コードは書かない）

- 上記順序で `validate` を構成し、ユニットテストで **許可／拒否境界**（`http://127.0.0.1`、プライベート帯、`[::1]`、`[::ffff:127.0.0.1]`、正規公網 IP、不正構文）を表にして網羅する。
- 既存 [`SsrfValidator`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/security/SsrfValidator.java) があるため、**差分が要件と一致しているか**をレビューし、ズレがあれば最小修正案だけを当てる。

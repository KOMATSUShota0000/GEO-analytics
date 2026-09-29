# GEO Analytics — プロジェクトルール

## 言語

- すべての対話と説明は**日本語**で行うこと
- 専門用語（クラス名・メソッド名・技術用語等）を除き、不必要な英語は避けること

## プロジェクト概要

GEO（Generative Engine Optimization）特化 B2B SaaS。Web制作会社・代理店が高単価GEO提案を行うための次世代レポート作成プラットフォーム。

**プロダクトの核（この4点から逸脱する実装は着手前にオーナーへ警告すること）:**

1. **WOW体験** — 4人のAIペルソナ（`ANALYST`（情報の番人, temp 0.1）＝原文からの事実抽出・根拠のないものは「不明」と明示／`SKEPTIC`（毒舌な競合, temp 0.4）＝独自性欠如・論理飛躍・根拠の弱さを批判／`INNOVATOR`（思考代弁者, temp 0.8）＝引用（Cite Before You Speak）に基づく独自の強みの提案／`DIRECTOR`（目利き・オーケストレーター, temp 0.2）＝盤石な合意案とマイノリティ・レポートへ構造化）による多角議論と改善ロードマップ自動生成
2. **実利** — 完全ホワイトラベル対応のSoM円グラフ・競合比較チャートを画像/コンポーネント出力
3. **SaaSグロース** — Teaser UI（ぼかし＋南京錠）によるProプランへのアップセル誘導
4. **高利益率** — 1解析＝1チケット消費、限界利益率86%死守

## 技術スタック

- **Backend**: Java 25, Spring Boot 3.5.13, LangChain4j 0.36.2, Sudachi
- **Database**: PostgreSQL 17（RLSによるテナント隔離、Flyway管理）
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, MUI (Emotion), Recharts
- **Security**: JWT + HttpOnly リフレッシュクッキー

## どこに何があるか

`application/service` は多数のファイルがフラットに並ぶ。ディレクトリでは絞れないため、以下を入口にすること。

| やりたいこと | 場所 |
|------|------|
| 解析ジョブの生成・永続化 | `application/service/Job*Service.java` |
| AIペルソナ議論・オンボーディング | `application/service/Debate*.java`、`infrastructure/ai/` |
| LLMプロンプト定義 | `infrastructure/ai/*Prompts.java` |
| LLM構造化出力の型 | `infrastructure/ai/*OutputSchema.java` |
| スコア算出ロジック | `domain/service/*Calculator*.java`、`domain/logic/` |
| テナント隔離（アプリ層） | `infrastructure/tenant/`（`ScopedValue` 伝搬） |
| テナント隔離（DB層） | RLSポリシーは各マイグレーション内のテーブル定義に同梱 |
| 課金（Stripe） | `application/billing/` |
| チケット消費・予約 | `application/credit/`（`CreditReservationAspect`） |
| クロール・本文抽出 | `infrastructure/crawler/` |
| 画面 | `frontend/src/pages/`、共通部品は `frontend/src/components/` |

## 触ってはいけない場所

`.claude/settings.json` で機械的にも禁止しているが、理由を以下に明記する。

| 対象 | 理由 |
|------|------|
| `.env` | APIキー・JWT秘密鍵・DBパスワードの実値。読み取りも禁止（`.env.example` を見ること） |
| `src/main/resources/db/migration/` の既存ファイル | 適用済みFlywayの**編集はチェックサム破壊で起動不能になる**。スキーマ変更は必ず新しい `V{次番号}__*.sql` を追加する |
| `docs/adr/` の既存ADR | 過去の決定記録は書き換えない。決定が覆ったら新しい日付のADRを追加する |
| `.cursorrules` | 当時の考えを残した**過去の記録**。消さない。**ルールとして参照・引用しない**（今のルールは下の「ルール」の章だけ） |
| `target/` / `node_modules/` | ビルド生成物。編集しても無意味で、検索対象にも含めない |

## SEO基盤とGEO残骸の扱い

GEOの土台にはSEOがある。すべてのSEO関連コードを消すのではなく、以下の基準で判断すること（R-32 も参照）。

| 扱い | 対象 |
|------|------|
| ✅ **残す** | Schema.org・構造化データ・JSON-LD |
| ✅ **残す** | robots.txt・llms.txt・クロール最適化 |
| ✅ **残す** | コンテンツ品質評価（LLMの引用可能性に関係するもの） |
| ❌ **削除** | `searchVolume` / `backlink` / `pageRank` / `domainAuthority` / `keywordRanking` |
| ❌ **削除** | キーワードボリューム取得・被リンク解析・SERP順位追跡のロジック |

## ルール

このプロダクトのルール（法）。issue・ADR・PR では番号（R-01 など）で指す。ルールを変えるときはこの章を直し、ADR を残す。2026-09-29 に、それまでの `.cursorrules` と CLAUDE.md のルールを棚卸しして作り直した（#178、ADR-088）。

### テナント隔離とデータベース

- **R-01** テナントの隔離は PostgreSQL の RLS で行う。`RlsConnectionInterceptor` が組織ID（`app.current_org_id`）とワークスペースID（`app.current_tenant_id`）を接続へ渡す。ORM の `@TenantId` や Hibernate Envers は使わない。
  - なぜ: アプリに不具合があっても、DB の層で他社のデータが見えないようにするため。
- **R-02** DB の読み書きは、`@Transactional` の付いたサービスのメソッドを、**別のクラスから**呼んで行う。リポジトリを直接呼ばない。
  - なぜ: 組織IDは、Spring のプロキシを通る `@Transactional` の呼び出しでしか接続に渡らない。リポジトリを直接呼ぶと、エラーも出ずに0件になる（お知らせが送られなかった #174、スナップショットが保存されなかった #180）。同じクラスの中から呼んでもプロキシを通らない。
- **R-03** 新しく起こした仮想スレッド（`Thread.ofVirtual()` など）には、呼び出し元のテナントの文脈が引き継がれない。中で DB に触るときは、テナントを束縛し直してから R-02 の形で呼ぶ（`@Async` は文脈を引き継ぐ）。
  - なぜ: #180 の原因。文脈がないと R-02 を守っても組織IDが渡らない。
- **R-04** DB は PostgreSQL が正。スキーマは Flyway で管理し、適用済みのマイグレーションは編集せず、新しい `V{次番号}__*.sql` を足す。AI の生の出力は JSONB で持つ。
- **R-05** DB に触る機能のテストは、RLS が効く接続（`PostgresTestBase` の `api_worker`）で書く。
  - なぜ: #174・#180 の不具合は、RLS を通らないテストでは見つからなかった。

### 並行処理と仮想スレッド

- **R-06** `ThreadLocal` は使わない。テナント情報は `ScopedValue` で渡す（複数あるときは `ScopedValue.where(...).where(...)` とつなぐ）。
  - なぜ: 仮想スレッドでは `ThreadLocal` がメモリを食い、別のテナントの値が混ざる危険がある。
- **R-07** 並列処理は `StructuredTaskScope` で行う。`parallelStream` と `Arrays.parallelSort` は使わない。
  - なぜ: 共有の ForkJoinPool（仮想スレッドの土台）を食いつぶし、アプリ全体が止まるため。
- **R-08** ロックは `synchronized` でも `ReentrantLock` でもよい。ただし、ロックを持ったまま外部の呼び出しや時間のかかる処理をしない。
  - なぜ: 以前は `synchronized` が仮想スレッドを止める（ピン留めする）ので禁止していたが、Java 24（JEP 491）で解消した。長くロックを持つと、それでも全体が詰まる。
- **R-09** AI などの外部 API の流量は、Bucket4j と Semaphore で絞る。`Thread.sleep` で待たない。
  - なぜ: 待つあいだ仮想スレッドを無駄に抱えず、テナントごと・全体の上限を論理的に守るため。
- **R-10** 仮想スレッドには、テナントやジョブのIDを含む名前を付ける。
  - なぜ: ログやスレッドダンプで、どの処理のスレッドかを追えるようにするため。

### AI との通信とストリーミング

- **R-11** Gemini Batch への投入と、ジョブ作成の API には Idempotency-Key（UUID）を付け、行ロック（`FOR UPDATE`）と組み合わせて二重実行を防ぐ。
- **R-12** SSE は `ObjectMapper` で JSON 文字列にしてから送る。`completeWithError` は使わず、エラーも JSON のイベントとして送る。WebSocket は使わない。
- **R-13** 時間のかかる処理では、SSE で解析の過程を見せる。
  - なぜ: 待っているあいだに作業の中身が見えると、利用者が価値を感じられるため（労働の錯覚）。
- **R-14** 利用者の入力やクロールしたページの内容は、AI に渡す前に無害化する（プロンプト・インジェクション対策）。
  - なぜ: 他社のページを AI に読ませるので、ページに仕込まれた指示に AI が従わないようにするため。

### 数理エンジン

- **R-15** スコアの計算には `StrictMath`（または `Math`）を使う。
  - なぜ: OS や CPU が変わっても、結果が1ビットも変わらないようにするため。
- **R-16** 数理の計算は1か所に集める（`RobustAuditMathUtil` など）。新しい計算を入れたら古い計算は消し、並べて残さない。
- **R-17** 数理エンジンの処理が重いところ（`computeBatch`・`forEachBigram` など）では、ループの中でオブジェクトを作らない。`int[]` とビットパッキングを使い、よく使う配列は必要なら使い回す（`ThreadLocal` は使わない）。画面や業務処理のコードでは読みやすさを優先する。
- **R-18** データが足りないときは、ダミーを足さず、もともと予定していた件数（計画上の分母）で補正する。
- **R-19** AI が「当てはまらない（false）」と判定したものは、統計の補正をせずに0点にする。
- **R-20** 感情スコアの補正には `StrictMath` を使い、ブランド名の出現が3%を超えたら放物線状に減点する（詰め込み対策）。
- **R-21** 文書の長さ・言及の数など確定した数は、AI に数えさせず、Java 側で数えて渡す。
- **R-22** 名寄せ（表記ゆれの統一）などは外部ライブラリに頼らず、Java の独自の仕組みで作る。

### 利益と品質

- **R-23** AI のコストを測り、1回の解析あたりのコストを売上の14%以内に収める（限界利益率86%）。
  - なぜ: プロダクトの核④。コストの計測はまだないので、#211 で入れる。
- **R-24** `SubscriptionIntegrationTest` を門番のテストにする（枠・429 からの回復・スレッドの健全性）。中核のロジックはテストなしで変えない。

### コードの書き方

- **R-25** DTO や変わらないデータには `record` を使う。JSON に対応づけるときは `@JsonProperty` をヘッダに付け、コンパクトコンストラクタで同じ名前のローカル変数を作り直さない。
- **R-26** 入力の境界は Bean Validation（`@Valid`・`@NotNull`・`@Size` など）で決める。
- **R-27** 名前は Java・TypeScript が camelCase、DB・JSON が snake_case。
- **R-28** ログは SLF4J を使う。`System.out` や、画面・ログへ直接出す `printStackTrace()` は使わない。スタックトレースは20,000文字で切って DB に保存する。
- **R-29** 書き方は周りのコードに合わせる。
- **R-30** コメントは下の「コメント規約」に従う。

### 製品と文言

- **R-31** 画面やメールの文言は、エンジニアでない利用者（代理店の担当者とそのクライアント）が説明なしで分かる言葉にする。内部用語を出さず、区別の中身で呼ぶ。
- **R-32** SEO 由来の機能（検索順位の追跡・検索ボリュームの取得・被リンク解析）は作らない。AI の回答での見え方（SoM など）を定期的に測るのは GEO の中心機能なので、これには当たらない。詳しい扱いは「SEO基盤とGEO残骸の扱い」の章。

### 作業の進め方

- **R-33** 複数のセッションで並行して開発するときは、自分の作業フォルダ（git worktree）で作業し、自分が変えたファイルだけを名指しでコミットする。PR の直前に、マイグレーションと ADR の番号が main や開いている PR と重ならないか確かめ、重なったら後から出す側が振り直す。
  - なぜ: 同じ番号のマイグレーションは Flyway の起動を止め、番号の重複は記録を追えなくするため。

## 作業前の必須手順

1. `Grep` / `Glob` / `Read` で既存コードを検索・熟読してから実装する（空想実装厳禁）
2. 実装完了後、ADR（技術決定記録）を `docs/adr/{YYYY-MM-DD}-{slug}.md` に残す

## 検証コマンド

```bash
# バックエンド（公式Maven Wrapper。Windows は mvnw.cmd）
./mvnw clean test

# フロントエンド（ルートには build スクリプトが無いので frontend/ で実行する）
cd frontend && npm run build

# 開発サーバー（Vite + Spring Boot を同時起動）
npm run dev
```

`scripts/mvnw.mjs` は npm スクリプト内から呼ぶためのシェル差異吸収ラッパー。
ターミナルから直接叩くときは `./mvnw` を使うこと。

**統合テストには Docker が必要。** `PostgresTestBase` / `PostgresSuperuserTestBase`
の派生テストは Testcontainers が使い捨てコンテナを自前で起動するため、
Docker さえ動いていれば通る（開発用DBは不要）。
Docker 未導入なら `sudo bash scripts/setup-docker-wsl.sh` で導入する。
アプリを起動するときの開発用DBは `bash scripts/db.sh up`。

環境構築の全手順・ハマりどころは `docs/DEVELOPMENT_SETUP.md` にまとめてある。

## 品質基準

- UIは一貫した世界観を持つこと（色、タイポグラフィ、レイアウトの統一）
- 各機能は実際に動作すること（スタブやモックで誤魔化さない）
- エッジケースのハンドリングを忘れないこと

## コメント規約

- 「何をしているか（What）」の自明なコメントは書かない
- 「なぜその実装・最適化を選択したか（Why）」のみ記述する
- 上の2つは **AI が新しく書くコメント** にだけ適用する。既存のコメントを消す理由にしない
- **既存のコメントは AI が消したり書き換えたりしない。** 消してよいのは、同じ作業の中で自分が書いたコメントだけ。それ以外のコメントを消す・直す必要があると思ったら、オーナーに確認する

  Why: ソースにはオーナーがコードリーディング中に書いた理解メモ（「〜してる」「ここまで読んだ」など）があり、What コメントに見えても消してはならない。書き手は文体や `git blame` では確実に見分けられないため、見分けずに守れる形にしている。

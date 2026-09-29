# GEO-analytics

生成AIが自社をどう語っているかを可視化する、GEO特化のB2B SaaSです。個人開発のプロトタイプとして作りました。
GEOは Generative Engine Optimization の略で、検索順位ではなく「AIの回答にどう引用されるか」を最適化する考え方を指します。
バックエンドは Java 25 の仮想スレッド、データ隔離に PostgreSQL の RLS、AI連携に LangChain4j と Gemini を使っています。

![status](https://img.shields.io/badge/status-MVP-blue) ![license](https://img.shields.io/badge/license-All%20Rights%20Reserved-red) ![java](https://img.shields.io/badge/Java-25-orange) ![spring](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen)

---

## デモ

<!-- TODO: スクリーンショット / デモ動画を後で差し込む -->

| シーン | キャプチャ |
|---|---|
| 4ペルソナAI議論ビュー | `docs/screenshots/01-debate.png` |
| GEO Readiness スコアとSoMの推移 | `docs/screenshots/02-readiness-trend.png` |
| Teaser UI — Proプラン誘導 | `docs/screenshots/03-teaser.png` |
| 価格プラン | `docs/screenshots/04-pricing.png` |

📹 デモ動画: `docs/demo.mp4`（後日アップロード予定）

---

## なぜ作ったか

生成AIが一気に進化したことで、人々の行動が「検索エンジンで探す」から「AIに直接聞く」へシフトしていると感じたのがきっかけです。このまま進めば従来の「検索」の価値は下がり、企業は検索順位を上げるSEOだけでは情報を届けられなくなる。だからこそ今後は「AIの回答に自社の情報が参照されるための最適化（GEO）」のニーズが爆発すると考え、いち早くその課題を解決するツールを作ろうと開発を始めました。

題材としても、AI・データ可視化・マルチテナントを一つに統合できる面白さがありました。マルチテナントのデータ隔離、二相課金、AIの議論オーケストレーション、ホワイトラベルといった、実際の B2B SaaS に必要な要素を、本物に近い品質で組み上げることを目標にしています。

事業性については、既存のSEOツールに内製アドオンとして組み込んだ方が現実的、といった制約も承知しています。このプロジェクトはあくまで技術検証とポートフォリオが目的です。

設計判断の背景は `docs/adr/` に90本のADRとして残してあります。

---

## プロダクトの核

| # | 価値 | 概要 |
|---|------|------|
| 1 | WOW体験 | 役割の異なる4人のAIペルソナ ANALYST / INNOVATOR / SKEPTIC / DIRECTOR が最大5ターン議論し、改善ロードマップを自動生成 |
| 2 | 実利 | 完全ホワイトラベル対応で、SoM・GEO Readiness スコアをレポート出力 |
| 3 | SaaSグロース | ぼかしと南京錠で見せる Teaser UI から Pro プランへ誘導 |
| 4 | 高利益率 | 1解析につき1チケット消費。課金は reserve → settle / refund の二相方式 |

---

## 実装状況

### 動作する機能
- 認証とセッション管理。メールで届くコードでログインし、セッションは JWT と HttpOnly リフレッシュクッキー方式
- マルチテナント隔離を PostgreSQL の Row Level Security で実装
- 4ペルソナのAI議論オーケストレーター。合意できた改善案と、合意できなかった少数意見（マイノリティ・レポート）を分けて出す
- 改善タスクの自動生成。番号順に進めたときの時間割を改善ロードマップとして表示
- スコア算出。AI回答内の言及度を表す SoM と、コンテンツ・技術・権威の3軸からなる GEO Readiness スコア
- 競合スニペットをRAGの根拠として取得。SerpAPI に本接続
- 二相課金。reserve → settle / refund を AOP で透過適用
- ホワイトラベル。ロゴとブランドカラーが MUI テーマと Recharts まで連動
- 競合とのシェア比較の円グラフ。自社をブランドカラーで塗り、提案書に貼れる1枚にまとめる
- Teaser UI による Pro プラン誘導
- ログイン不要の公開デモページ。本番と同じ画面部品にサンプルデータを流して見せる
- 価格プラン画面。STANDARD / PRO / EXPERT の3プランを比較
- Stripe のセルフサーブ決済。Checkout セッションの発行と Webhook 受信でプランを同期
- レポートのPDF出力。ブラウザの印刷機能を使う方式
- ジョブ完了を起点に走る `GeoAssetSnapshotPipeline`。90日分のトレンドを蓄積
- 解析完了のお知らせメール。SoM の平均と前回との差を、プロジェクトごとに3件までの宛先へ送る

### 部分実装
- マルチAIモデル対応。今は Gemini 単独で、ChatGPT と Claude はプランの枠だけ用意してある
- 深層分析バッチ。現状はプレースホルダ実装

### 未着手
- テナント横断の管理者向けダッシュボード

---

## アーキテクチャの見どころ

このプロジェクトで特に時間をかけたのは、**テナント隔離**と**チケット課金**の2つです。

### マルチテナント隔離は PostgreSQL の RLS で担保
36テーブルに Row Level Security ポリシーを適用しています。すべての `@Transactional` に対して AOP が `set_config('app.current_org_id', ...)` を実行するので、テナントの絞り込みはDB側で効きます。担当は `RlsConnectionInterceptor`。アプリ層でフィルタを書き忘れても、DBが他社の行を遮断する設計です。

### 二相課金
`CreditVaultService` が reserve → settle / refund を `@CreditReservation` の AOP で透過的に適用します。二重課金は、DBの行ロック `findByIdForUpdate` と `existsByParentReservationId` の子チェックでDB層から防ぎます。JVMが異常終了して残った孤児の RESERVE は、毎時動く `StaleReservationSweeper` が自動で回収します。

### その他の構成
- Java 25 の仮想スレッドを前提にした構成。テナントコンテキストの伝播は `ScopedValue` を使用
- 4ペルソナのAIが多角的に議論し、改善案を提示
- JWT と HttpOnly リフレッシュクッキー方式の認証（jjwt）
- ホワイトラベルのロゴとブランドカラーが MUI テーマと Recharts まで連動
- Flyway によるDBスキーマ管理。49マイグレーション、最新は V145
- 約400件のテスト。unit と integration があり、PostgreSQL は Testcontainers を使用
- GitHub Actions による CI。PR と main への push のたびに、テストとフロントエンドのビルドを実行

---

## 技術的にこだわった点・ハマった点

### 1. テナント隔離を「書き忘れても漏れない」形にした

複数の会社が同じシステムを使う以上、他社のデータが1行でも見えたら終わりです。最初はアプリ側のクエリに会社IDの条件を足す方法も考えましたが、これだとどこか1か所で条件を書き忘れた瞬間に漏れます。人間はいつか必ず書き忘れる、という前提で設計したかった。

そこで PostgreSQL の Row Level Security を使い、隔離をDB側に持たせました。仕組みは3段階です。RLSはテーブルの所有者には効かないので、まず権限を絞った専用ロール `api_worker` でDBに接続します。次に、リクエストごとに AOP が `set_config` で「今どの会社か」をDBセッションに書き込みます。最後に、各テーブルのポリシーが「自分の会社の行しか読めない・書けない」を強制します。

ハマったのは、トランザクションの外でうっかりDBにアクセスすると、会社IDがセットされないまま素通りしてしまう点でした。これを塞ぐため、`@Transactional` の無いDBアクセスは例外で止める安全装置を入れています。RLSが効かない状態でのアクセスそのものを、設計で起こせないようにしたかったからです。

学びは、セキュリティはどこか1枚の壁に頼るのではなく、アプリとDBの多層で持つべきだということでした。

### 2. チケット課金を「失敗しても二重課金しない・取りっぱなしにしない」形にした

解析1回につきチケットを1枚消費する課金モデルです。AIが動くぶん処理に時間がかかり、その間に残高チェックをすり抜けて二重課金されたり、途中で失敗してチケットだけ取られたりする事故が起きやすい。

対策として、reserve → settle / refund の二相方式にしました。解析の前にチケットを予約して先に引き、成功したら使った分で精算、失敗したら全額返金します。二重課金は、組織の残高行に悲観ロックをかけて同時実行を直列化したうえで、「1つの予約に精算・返金は1回まで」を親予約の存在チェックで保証して防いでいます。

ハマったのは、失敗時の返金を `finally` に置いたとき、その返金がさらに失敗すると元のエラーが消えてしまうことでした。Javaの `finally` の仕様です。返金を `try-catch` で包んで元のエラーを優先し、取りこぼした予約は毎時動く `StaleReservationSweeper` が後から回収するようにしました。サーバーが突然落ちてもチケットが永久に凍結されない、自己修復する作りです。

学びは、お金を扱うコードは「成功する道」より「失敗する道」を丁寧に設計しないといけない、ということでした。詳細は ADR-003 にあります。

---

## 技術スタック

| 区分 | 採用 |
|---|---|
| 言語・ランタイム | Java 25。preview機能の `StructuredTaskScope` を使用。フロントは Node 22 |
| バックエンド | Spring Boot 3.5.13、Spring Security、Spring Data JPA、AOP、WebFlux、Spring Mail |
| AI基盤 | LangChain4j 0.36.2、Google Gemini の `gemini-2.5-flash` と `gemini-2.5-pro`、Apache Tika |
| 形態素解析 | Sudachi。日本語の N-gram とエンティティ正規化に使用 |
| DB | PostgreSQL 17 の Row Level Security、Flyway、HikariCP。プールは api と batch の2系統 |
| キャッシュ・レート制御 | Caffeine、Bucket4j |
| 決済 | Stripe。Checkout と Webhook を stripe-java で連携 |
| フロントエンド | React 18、TypeScript 5.3、Vite 5、Tailwind CSS、MUI 5、Recharts |
| テスト | JUnit 5、Testcontainers、Awaitility、H2 |
| その他 | GitHub Actions による CI、CycloneDX による SBOM 生成、spring-dotenv によるローカルの `.env` 読み込み |

---

## ローカルでの動かし方

> 採用担当者によるレビュー目的での動作確認は想定内です。
> 個人プロジェクトでの利用や派生作品の作成は、ライセンス上できません。詳細は [`LICENSE`](./LICENSE) を参照してください。

### 前提
- WSL2 の Ubuntu。ツールの導入まで含めた全手順は [`docs/DEVELOPMENT_SETUP.md`](./docs/DEVELOPMENT_SETUP.md) にあります
- JDK 25。preview 有効
- Node.js 22 以上
- Docker。PostgreSQL と開発用のメール受信箱 Mailpit、テスト時の Testcontainers で使います

### セットアップ

```bash
# 1. .env を作成（実値は各自で用意）
cp .env.example .env
# .env を開いて GEMINI_API_KEY 等の実値を埋める。
# 必須: GEMINI_API_KEY / JWT_SECRET / FLYWAY_PASSWORD / API_WORKER_PASSWORD / BATCH_WORKER_PASSWORD
# 任意: SERPAPI_API_KEY / GOOGLE_PLACES_API_KEY / REDIS_HOST / STRIPE_*

# 2. DB とメール受信箱を起動
bash scripts/db.sh up
bash scripts/mail.sh up

# 3. バックエンド
./mvnw spring-boot:run

# 4. フロントエンド
cd frontend
npm install
npm run dev
```

必要な環境変数は [`.env.example`](./.env.example) にまとめてあります。実値は絶対にコミットしないでください。`.env` は `.gitignore` に登録済みです。

ログインにパスワードは使いません。http://localhost:5173/login で初期ユーザー `bootstrap@example.com` を入れると、ログインコードが Mailpit（http://localhost:8025）に届きます。

### テスト

```bash
./mvnw clean test
```

---

## プロジェクト構成（抜粋）

```
.
├── src/main/java/com/geo/analytics/   # バックエンド本体
│   ├── application/                   # ユースケース層。DTO・サービス・セキュリティ
│   ├── domain/                        # ドメインモデル・例外
│   ├── infrastructure/                # アダプタ・設定・永続化・LLMクライアント
│   └── web/                           # REST コントローラ・DTO
├── src/main/resources/
│   ├── application*.yml               # プロファイル別設定。すべて env 参照
│   └── db/migration/                  # Flyway マイグレーション
├── frontend/                          # React + TypeScript（Vite）
│   └── src/
│       ├── pages/                     # JobAnalysisPage / StrategyDashboardPage / PricingPage 等
│       └── components/                # チャート・テーマ・Teaser UI
└── docs/
    ├── adr/                           # 技術決定記録（ADR）90本
    └── screenshots/                   # スクリーンショット
```

---

## ライセンス・連絡先

- ライセンス: All Rights Reserved。詳細は [`LICENSE`](./LICENSE)。ソースコードは閲覧専用です
- 連絡先: GitHub プロフィール経由 — https://github.com/KOMATSUShota0000

技術的なフィードバックや、採用観点でのご連絡は歓迎します。利用許諾が必要な場合はご相談ください。

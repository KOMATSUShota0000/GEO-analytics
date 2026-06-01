# GEO-analytics

**LLM が自社をどう語っているか**を可視化する GEO（Generative Engine Optimization）特化の B2B SaaS プロトタイプ。
Java 25 仮想スレッド × PostgreSQL RLS × LangChain4j（Gemini）で構築した個人開発プロジェクト。

![status](https://img.shields.io/badge/status-MVP-blue) ![license](https://img.shields.io/badge/license-All%20Rights%20Reserved-red) ![java](https://img.shields.io/badge/Java-25-orange) ![spring](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen)

---

## デモ

<!-- TODO: スクリーンショット / デモ動画を後で差し込む -->

| シーン | キャプチャ |
|---|---|
| 4ペルソナAI議論ビュー | `docs/screenshots/01-debate.png` |
| SoM 円グラフ・競合比較 | `docs/screenshots/02-som-chart.png` |
| Teaser UI（Pro誘導） | `docs/screenshots/03-teaser.png` |
| 価格プラン | `docs/screenshots/04-pricing.png` |

📹 デモ動画: `docs/demo.mp4`（後日アップロード予定）

---

## なぜ作ったか

個人開発の学習プロジェクトとして、**B2B SaaS の構成要素を一通り実装してみる**ことを目的に着手した。マルチテナント隔離・二相課金・AI オーケストレーション・ホワイトラベル・SSE ストリーミング配信といった、実 SaaS で求められる要素を本物に近い品質で組み上げることに集中している。

題材として GEO（Generative Engine Optimization）を選んだのは、生成 AI が検索体験を変えつつあるなかで「自社が LLM にどう引用されているか」を可視化するという問題設定が、**AI × データ可視化 × マルチテナント**を一つに統合する題材として面白かったから。

事業性については **既存 SEO ツールへの内製アドオンの方がコスパ良い**等の現実的な制約もあると認識しており、本プロジェクトはあくまで技術検証・ポートフォリオ目的で公開している。

意思決定の背景は `docs/adr/`（10本のADR）にすべて残してある。

---

## プロダクトの核

| # | 価値 | 概要 |
|---|------|------|
| 1 | **WOW 体験** | 4人の AI ペルソナ（ANALYST / INNOVATOR / SKEPTIC / DIRECTOR）が最大5ターン議論し、改善ロードマップを自動生成 |
| 2 | **実利** | 完全ホワイトラベル対応の SoM 円グラフ・競合比較チャート出力 |
| 3 | **SaaS グロース** | Teaser UI（ぼかし＋南京錠）による Pro プランへのアップセル誘導 |
| 4 | **高利益率** | 1解析＝1チケット消費、reserve→settle/refund の二相課金 |

---

## 実装状況

### ✅ 動作する機能
- 認証（JWT + HttpOnly リフレッシュクッキー）・セッション管理
- マルチテナント隔離（PostgreSQL Row Level Security）
- 4ペルソナ AI 議論オーケストレーター（SSE ストリーミング配信）
- GEO 可視性スコア算出（較正済み信頼度 / GEO-IG スカラー）
- 競合エビデンス取得（SerpAPI 本接続）・合成競合フォールバック
- 二相課金（reserve → settle/refund、AOP 経由）
- ホワイトラベル（ロゴ・ブランドカラーが MUI テーマ・Recharts まで連動）
- Teaser UI による Pro プラン誘導
- 価格プラン画面（STANDARD / PRO / EXPERT の3プラン比較表）
- ジョブ完了駆動の `GeoAssetSnapshotPipeline`（90日分トレンド蓄積）

### ⚠ 部分実装
- **決済**: Stripe 未接続。現状はメール問い合わせで Pro プランデモを受ける運用（ADR で意図的に後回しと記録）
- **PDF 解析**: Apache Tika ベースで動くが Docker 環境でのフォント問題あり（`docs/PDF_DOCKER_NOTES.md`）

### ❌ 未着手
- Stripe 課金 Webhook
- 管理者向けダッシュボード（テナント横断）

---

## アーキテクチャの見どころ

### マルチテナント隔離は PostgreSQL **RLS** で担保
- **36テーブル**に Row Level Security ポリシーを適用
- 全 `@Transactional` に AOP が `SET LOCAL app.current_tenant = ...` を注入（`RlsConnectionInterceptor`）
- アプリ層のフィルタ忘れでも DB が遮断する設計

### 仮想スレッドを前提とした Java 25 構成
- テナントコンテキスト伝播は `ScopedValue`
  → **`ThreadLocal` 禁止**（仮想スレッドでのメモリリーク・ピン留め回避）
- `parallelStream` / `synchronized` ホットパス禁止
  → ForkJoinPool 枯渇・キャリアスレッドピン留め対策
- 並列化が必要な場面は `StructuredTaskScope` / `ConcurrentLinkedDeque`

### 二相課金（reserve → settle/refund）
- `CreditVaultService` が `reserve → settle/refund` を AOP（`@CreditReservation`）で透過適用
- 二重課金防止は DB 行ロック (`findByIdForUpdate`) ＋ `existsByParentReservationId` 子チェックで **DB 層担保**
- JVM 異常終了時の孤児 RESERVE は `StaleReservationSweeper`（cron 毎時）が自動回収

### 4ペルソナ AI 議論オーケストレーター
- `DebateOnboardingOrchestrator` が業種別ペルソナ（YMYL / EC / B2B / B2C / LOCAL / OTHER）で議論を生成
- 較正済み信頼度（calibrated confidence）と GEO-IG スカラーをスコアとして算出
- 進捗は SSE で配信（`SseEmitter`、リトライ付き指数バックオフ接続）

### その他
- JWT (jjwt) + HttpOnly リフレッシュクッキー方式の認証
- ホワイトラベル（ロゴ・ブランドカラー）が MUI テーマ・Recharts まで連動
- Flyway による DB スキーマ管理（31マイグレーション、最新 V127）
- **164 件**のテスト（unit + integration、Testcontainers の PostgreSQL を使用）

---

## 技術的にこだわった点・ハマった点

### 1. アーキテクチャ違反の自主検出 → 修正（ADR-002）

Java 25 仮想スレッド前提のはずなのに、コードベース内に `parallelStream` と SSE ナレーションバッファでの `synchronized` が残っていた。これは：
- `parallelStream`: 共有 ForkJoinPool を枯渇させる
- `synchronized`: 仮想スレッドをキャリアスレッドにピン留めしてスケーラビリティを殺す

QA監査エージェントを別途立てて自動検出 → `ReentrantLock` 置換 / 逐次処理化で解消した。
**学び**: 仮想スレッド時代の「やってはいけないこと」は Loom リリースノート以外に体系的にまとまっていないので、自分でルール化して `.cursorrules` に明文化する必要があった。

### 2. 設計コメントとコードの乖離を発見（ADR-008）

`SerpApiKeyStartupCheck` のコメントには「APIキー未設定でも例外を投げず起動を止めない（プレースホルダ降格は既存設計）」と書いてあるのに、別ファイル `AsyncSgeMeasurementService:67` が普通に `IllegalStateException` を投げてジョブ全滅させていた。

**学び**: ADR や設計コメントは「真実」ではなく「意図」でしかない。実装の挙動が意図と乖離していないか、定期的に audit する仕組みが必要だと痛感した。

### 3. 「合成競合」を意味のあるデータに進化（ADR-006）

実競合が規定数に満たないとき UI にダミー競合を出すが、当初は3体とも同じテンプレ文だった。これは「核①: WOW 体験」を裏切る。
発生理由を `SyntheticPadReason` enum 化（候補ゼロ / 実競合不足 / フィルタAI例外）して、序数ごとに「優位 / 中央値 / 改善余地」の参照ティアを割り当て、3体を意味的に差別化した。

**学び**: 「動く」と「価値を出す」は別物。プレースホルダの質も製品価値に直結する。

---

## 技術スタック

| 区分 | 採用 |
|---|---|
| 言語・ランタイム | **Java 25**（preview features: `ScopedValue`）・Node 22 |
| バックエンド | Spring Boot 3.5.13、Spring Security、Spring Data JPA、AOP、WebFlux、SSE |
| AI 基盤 | LangChain4j 0.36.2、Google Gemini (`gemini-2.5-flash`)、Apache Tika |
| 形態素解析 | Sudachi（日本語 N-gram・エンティティ正規化） |
| DB | PostgreSQL 17（Row Level Security）／Flyway／HikariCP（2系統プール: api / batch） |
| キャッシュ・レート制御 | Caffeine、Bucket4j |
| フロントエンド | React 18、TypeScript 5.3、Vite 5、Tailwind CSS、MUI 5、Recharts |
| テスト | JUnit 5、Testcontainers、Awaitility、H2（軽量テスト用） |
| その他 | CycloneDX SBOM 生成、spring-dotenv（ローカル開発の `.env` ロード） |

---

## ローカルでの動かし方

> 採用担当者によるレビュー目的での動作確認は想定内です。
> 個人プロジェクトでの利用や派生作品の作成はライセンス上できません（[`LICENSE`](./LICENSE) 参照）。

### 前提
- JDK 25（preview 有効）
- Node.js 22+
- Docker（PostgreSQL を Testcontainers / ローカル DB 用に起動）

### セットアップ

```powershell
# 1. .env を作成（実値は各自で用意）
Copy-Item .env.example .env
# .env を開いて GEMINI_API_KEY 等の実値を埋める。
# 必須: GEMINI_API_KEY / JWT_SECRET / FLYWAY_PASSWORD
# 任意: SERPAPI_API_KEY / GOOGLE_PLACES_API_KEY / REDIS_HOST

# 2. バックエンド
.\mvnw.cmd spring-boot:run

# 3. フロントエンド
cd frontend
npm install
npm run dev
```

必要な環境変数の一覧は [`.env.example`](./.env.example) を参照。**実値は絶対にコミットしないこと**（`.gitignore` に `.env` 登録済み）。

### テスト

```powershell
.\mvnw.cmd clean test
```

---

## プロジェクト構成（抜粋）

```
.
├── src/main/java/com/geo/analytics/   # バックエンド本体
│   ├── application/                   # ユースケース層（DTO・サービス・セキュリティ）
│   ├── domain/                        # ドメインモデル・例外
│   ├── infrastructure/                # アダプタ・設定・永続化・LLMクライアント
│   └── web/                           # REST コントローラ・DTO
├── src/main/resources/
│   ├── application*.yml               # プロファイル別設定（全て env 参照）
│   └── db/migration/                  # Flyway マイグレーション
├── frontend/                          # React + TypeScript（Vite）
│   └── src/
│       ├── pages/                     # JobAnalysisPage / StrategyDashboard / PricingPage 等
│       └── components/                # チャート・テーマ・Teaser UI
└── docs/
    ├── adr/                           # 技術決定記録（ADR）10本
    └── screenshots/                   # スクリーンショット
```

---

## ライセンス・連絡先

- ライセンス: **All Rights Reserved** ([`LICENSE`](./LICENSE)) — ソースコードは閲覧専用です
- 連絡先: GitHub プロフィール経由 — https://github.com/KOMATSUShota0000

技術的なフィードバックや採用観点での連絡は歓迎します。利用許諾が必要な場合はご相談ください。

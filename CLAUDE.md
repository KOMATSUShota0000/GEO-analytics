# GEO Analytics — プロジェクトルール

@.cursorrules

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

`application/service` は67ファイルがフラットに並ぶ。ディレクトリでは絞れないため、以下を入口にすること。

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
| `target/` / `node_modules/` | ビルド生成物。編集しても無意味で、検索対象にも含めない |

## SEO基盤とGEO残骸の扱い

GEOの土台にはSEOがある。すべてのSEO関連コードを消すのではなく、以下の基準で判断すること。

| 扱い | 対象 |
|------|------|
| ✅ **残す** | Schema.org・構造化データ・JSON-LD |
| ✅ **残す** | robots.txt・llms.txt・クロール最適化 |
| ✅ **残す** | コンテンツ品質評価（LLMの引用可能性に関係するもの） |
| ❌ **削除** | `searchVolume` / `backlink` / `pageRank` / `domainAuthority` / `keywordRanking` |
| ❌ **削除** | キーワードボリューム取得・被リンク解析・SERP順位追跡のロジック |

## アーキテクチャ絶対禁止事項

全文はこのファイル冒頭で読み込んでいる `.cursorrules` にある。主要な禁止事項の要約:

| 禁止 | 代替 |
|------|------|
| `ThreadLocal` | `ScopedValue` (Carrier API) |
| `WebSocket` | `SSE` (SseEmitter) |
| `Hibernate Envers` / ORM `@TenantId` | PostgreSQL RLS |
| `parallelStream` / `Arrays.parallelSort` | `StructuredTaskScope` |
| `synchronized` ブロック（ホットパス） | `ReentrantLock` / `Semaphore` |
| 外部ライブラリ（名寄せ等） | 100% Pure Java独自エンジン |

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
の派生テスト（18件）は Testcontainers で PostgreSQL 17 を起動する。
Docker 未導入なら `sudo bash scripts/setup-docker-wsl.sh` で導入し、
開発用DBは `bash ~/geo-analytics-db.sh up` で起動する。

## 品質基準

- UIは一貫した世界観を持つこと（色、タイポグラフィ、レイアウトの統一）
- 各機能は実際に動作すること（スタブやモックで誤魔化さない）
- エッジケースのハンドリングを忘れないこと

## コメント規約

- 「何をしているか（What）」の自明なコメントは書かない
- 「なぜその実装・最適化を選択したか（Why）」のみ記述する

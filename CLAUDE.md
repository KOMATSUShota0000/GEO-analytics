# GEO Analytics — プロジェクトルール

## 言語

- すべての対話と説明は**日本語**で行うこと
- 専門用語（クラス名・メソッド名・技術用語等）を除き、不必要な英語は避けること

## プロジェクト概要

GEO（Generative Engine Optimization）特化 B2B SaaS。Web制作会社・代理店が高単価GEO提案を行うための次世代レポート作成プラットフォーム。

**プロダクトの核（この4点から逸脱する実装は着手前にオーナーへ警告すること）:**

1. **WOW体験** — 4人のAIペルソナ（LLM可読性エンジニア・AI-PRストラテジスト・GEOデータサイエンティスト・AI戦略CMO）による多角議論と改善ロードマップ自動生成
2. **実利** — 完全ホワイトラベル対応のSoM円グラフ・競合比較チャートを画像/コンポーネント出力
3. **SaaSグロース** — Teaser UI（ぼかし＋南京錠）によるProプランへのアップセル誘導
4. **高利益率** — 1解析＝1チケット消費、限界利益率86%死守

## 技術スタック

- **Backend**: Java 25, Spring Boot 3.5.13, LangChain4j 0.36.2, Sudachi
- **Database**: PostgreSQL 17（RLSによるテナント隔離、Flyway管理）
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, MUI (Emotion), Recharts
- **Security**: JWT + HttpOnly リフレッシュクッキー

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

詳細は `.cursorrules` を参照。主要な禁止事項を以下に要約する:

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
2. 実装完了後、ADR（技術決定記録）を `docs/adr/` に残す
3. テスト: `./mvnw clean test`（バックエンド）、`npm run build`（フロントエンド）

## 実装完了後の必須手順（dev-status 更新）

コーダーがスプリントを完了したら、秘書へ報告する前に必ず以下を実行すること:

1. `C:\cursor\company\.company\secretary\notes\dev-status.md` を開く
2. 実装した機能を「✅ 実装済み」テーブルに追記する
3. 部分実装・変更した項目を「🔧 実装中」テーブルで更新する
4. 解消した懸念点を「🚨 重要な懸念点」から削除または取り消し線で済ませる
5. 新たに発見した懸念点を追記する
6. `最終更新:` 日付を更新する

## 品質基準

- UIは一貫した世界観を持つこと（色、タイポグラフィ、レイアウトの統一）
- 各機能は実際に動作すること（スタブやモックで誤魔化さない）
- エッジケースのハンドリングを忘れないこと

## コメント規約

- 「何をしているか（What）」の自明なコメントは書かない
- 「なぜその実装・最適化を選択したか（Why）」のみ記述する

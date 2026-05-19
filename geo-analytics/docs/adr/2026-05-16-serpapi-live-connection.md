# ADR-001: SerpAPI 競合エビデンス本接続

## 日付

2026-05-16

## 状況

`ProjectOnboardingService.buildPlaceholderSeoRows()` が返すダミー1件（自社サイトのみ）を競合エビデンスとして4人のAIペルソナに渡していた。これでは「根拠ある議論」が成立せず、WOW体験の品質が担保されない。

## 決定

- `buildPlaceholderSeoRows()` を廃止し `buildCompetitorEvidenceRows()` に置換した
- 内部で `GeoCompetitorSearchService.searchOrganic()` を呼び出し、SerpAPI の有機検索結果を `GeoEvidenceRow` に変換して渡す
- 自社ドメインは `URI.getHost()` ベース（`www.` 正規化あり）で除外する
- SerpAPI キー未設定・クレジット不足・ネットワークエラー時はプレースホルダ1件にフォールバックしオンボーディングを止めない
- `application.yml` の `app.serpapi.api-key` を `${SERPAPI_API_KEY:}` に変更し、本番は環境変数から注入する

## 理由

- **エラー透過性**: フォールバック設計によりオンボーディング全体がSerpAPIエラーに依存しない
- **クレジット管理**: `GeoCompetitorSearchService` が既に `CreditVaultService.reserve/settle/refund` を実装しており、1オンボーディング1呼び出しのコスト制御が保証される
- **シンプルな実装**: 既存の `searchOrganic()` → `GeoEvidenceRow` の変換は単純な1:1マッピングで済み、外部ライブラリ不要（Pure Javaのみ）
- **プランアップセル効果**: `maxCompetitorEvidenceXmlChars()` の差異（Standard 12k vs Pro 24k vs Expert 48k）が実際の競合データで意味を持つようになり、プランアップグレードのインセンティブが生まれる

## 結果

- SerpAPI キーが有効な環境: オンボーディング時に最大15件の競合URLがGeoEvidenceRowとして渡される
- SerpAPI キー未設定（開発環境）: プレースホルダ1件にフォールバック、動作に変化なし
- クレジット消費: オンボーディング1回あたり追加で30クレジット（SERP_ORGANIC_CREDIT）が消費される
- 本番切り替え: `SERPAPI_API_KEY` 環境変数に実キーを設定するだけで有効化できる

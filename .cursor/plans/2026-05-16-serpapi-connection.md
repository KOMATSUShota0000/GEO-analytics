# 仕様書: SerpAPI 競合エビデンス本接続

## 目的

`ProjectOnboardingService` の `buildPlaceholderSeoRows()` が返すダミーデータ1件を、実際の SerpAPI 呼び出しで取得した競合の有機検索スニペット群に差し替える。

現状、4人の AI ペルソナ（ANALYST・INNOVATOR・SKEPTIC・DIRECTOR）が議論するための競合エビデンス（`GeoEvidenceRow`）が自社サイト1件のプレースホルダのみで構成されており、議論の根拠が欠如している。実競合データを LLM ディベートの入力に接続することで、WOW 体験の品質（「根拠ある AI ペルソナ議論」）を完成させる。

## 対象ユーザー

- 新規プロジェクトのオンボーディング（初回 URL 解析）を行うすべてのユーザー
- 特に競合比較の根拠を重視するエンタープライズ・代理店向けユーザー

## 機能要件

- オンボーディング時（`ProjectOnboardingService.runGeoPipeline()` 内）に、解析対象 URL のドメインやキーワードを使って SerpAPI の有機検索結果を取得すること
- 取得した有機検索結果（`SerpOrganicResult` のリスト）を `GeoEvidenceRow` に変換し、`debateOnboardingOrchestrator.runDebateOnboarding()` に渡すこと
- 変換の際、自社 URL（引数の `pageUrl`）と一致するドメインの結果は除外すること（自社が競合リストに入らないように）
- SerpAPI のレスポンスが空、またはエラーになった場合は、現状のプレースホルダ（自社サイト1件）にフォールバックすること（オンボーディングを止めない）
- SerpAPI キーが未設定または空の場合も同様にフォールバックすること

## UI/UX 要件

- ユーザーには特段の変化はない。オンボーディング SSE ストリーミングのナレーションメッセージに「競合情報を収集しています」等のフェーズメッセージを追加することが望ましい（`DebateOnboardingSseEvent.DebateStreamPhase.GATHERING` フェーズ相当）
- 競合エビデンスの取得に失敗してもオンボーディング全体がエラーにならないこと（エラー透過性の保証）

## データ要件

### 入力

- オンボーディング対象の URL（`pageUrl`）
- URL から導出される検索クエリ（ホスト名ベース。既存の `extractSearchQueryHint()` を活用する）

### 処理フロー

```
1. extractSearchQueryHint(uri) で検索クエリを生成（既存ロジックを流用）
2. GeoCompetitorSearchService.searchOrganic(projectId, searchQuery) で有機検索結果を取得
   └─ SerpAPI キー未設定・エラー時は空リストを返す（既存実装通り）
3. SerpOrganicResult → GeoEvidenceRow への変換
   - url:     SerpOrganicResult.link()
   - title:   SerpOrganicResult.title()
   - snippet: SerpOrganicResult.snippet()
   - relevanceLabel: Optional.empty()（既存の GeoEvidenceRow コンストラクタに合わせる）
   - score:   Optional.empty()
4. 自社 URL と同一ドメインの行を除外する（URI.getHost() で比較）
5. 結果が空の場合は buildPlaceholderSeoRows(url) の結果で代替
6. debateOnboardingOrchestrator.runDebateOnboarding() に渡す
```

### SerpAPI キー設定

- `application.yml` の `app.serpapi.api-key` に実際のキーを設定する必要がある
- 現状はダミーキーが設定されており、開発環境では SerpAPI 呼び出しが失敗してプレースホルダにフォールバックする（既存の `GeoCompetitorSearchService.searchOrganic()` がキー未設定時に空リストを返す実装になっているため、この動作は保証される）
- 本番環境では `SERPAPI_API_KEY` 環境変数から注入することを推奨する（`application.yml` の `app.serpapi.api-key: "${SERPAPI_API_KEY:}"` 形式に変更する）

### クレジット消費

- `GeoCompetitorSearchService.searchOrganic()` は内部で `CreditVaultService.reserve()` を呼び出すため、1回の SerpAPI 呼び出しで 30 クレジットを消費する（既存の `SERP_ORGANIC_CREDIT = 30L` 定義通り）
- オンボーディング1回あたり SerpAPI 呼び出しは1回に限定すること（複数クエリの発行は禁止。LLM 呼び出しコスト増と同様の理由）
- クレジット消費が発生することを事前に考慮し、プロジェクトに十分なクレジットがない場合の例外ハンドリング（`InsufficientQuotaException` 等）はプレースホルダフォールバックで吸収すること

## プロダクトの核との整合性

- **WOW体験**: 本仕様の中核。「根拠あるデータに基づいた4人の AI ペルソナ議論」が実現する。競合の実際のスニペットが LLM コンテキストに入ることで、ペルソナの議論がより具体的・説得力のあるものになる
- **実利**: 競合比較レポートの精度向上。ホワイトラベルのレポートに実エビデンスが入ることで代理店への提案価値が高まる
- **SaaSグロース**: `maxCompetitorEvidenceXmlChars()` の差異（Standard 12,000文字 vs Pro 24,000文字 vs Expert 48,000文字）が実際に意味を持つようになる。プランアップグレードのインセンティブが生まれる
- **高利益率**: 1オンボーディングあたり SerpAPI 呼び出し1回のみ（30クレジット）。LLM 呼び出し回数は増えない

## 注意事項・懸念点

1. **SerpAPI キーの本番設定が必須**: 現状のダミーキー `deca9c28024fb31...` では本番で動作しない。本番デプロイ前に実キーへの切り替えが必要
2. **`projectId` の存在保証**: `GeoCompetitorSearchService.searchOrganic()` の第1引数 `projectId` が `null` の場合は空リストが返るため、オンボーディング時点でプロジェクト ID が確定していること前提の設計になっている。呼び出し側でプロジェクト ID の存在を確認すること
3. **ドメイン比較の精度**: 自社 URL の除外を `URI.getHost()` ベースで行う場合、`www.` の有無の違いで一致しないケースがある。簡易的には `host.replace("www.", "")` で正規化すること
4. **SerpAPI レート制限**: `SerpApiGlobalRequestGate` による全体レート制限は既存実装で対応済みのため、追加対応不要

## スプリント（タスク）分割案

- [ ] Sprint 1: `buildPlaceholderSeoRows()` を `buildCompetitorEvidenceRows()` にリネームし、内部で `GeoCompetitorSearchService.searchOrganic()` を呼び出す実装に差し替える。エラー時のプレースホルダフォールバックを含む
- [ ] Sprint 2: `SerpOrganicResult` → `GeoEvidenceRow` の変換ロジックを実装し、自社ドメインの除外処理を追加する
- [ ] Sprint 3: `application.yml` の `app.serpapi.api-key` を環境変数インジェクション形式（`${SERPAPI_API_KEY:}`）に変更し、ADR に本接続の決定を記録する

## 受け入れ条件

- SerpAPI キーが有効な環境では、オンボーディング時に実際の有機検索結果が `GeoEvidenceRow` に変換され、`runDebateOnboarding()` に渡される
- 自社 URL と同一ドメインの行が競合リストから除外されている
- SerpAPI キーが空またはダミーの場合、プレースホルダ1件にフォールバックし、オンボーディングが正常完了する
- SerpAPI 呼び出しのエラー（タイムアウト・HTTP エラー等）でオンボーディング全体が止まらない
- 既存の39個のテストが引き続きパスすること（`GeoCompetitorSearchService` のモックによるテストが必要な場合は追加する）
- `./mvnw clean test` が通ること

## 成果物の保存先

`C:\cursor\project\.cursor\plans\2026-05-16-serpapi-connection.md`

# ADR-067: テナント所属判定をトランザクション内で行う

## 日付
2026-09-21

## 状況

料金ページの開発・検証用プラン切替が画面に反映されなかった（#131）。

- 切替（`PATCH /api/v1/workspaces/{id}/subscription`）はDBに反映されていた
- 取得（`GET /api/v1/workspaces/{id}`）が、所属組織のADMINでも常に **403** だった
- `GET /api/v1/workspaces/current/branding` も同じく403で、ホワイトラベルのブランディングも読めていなかった

`@PreAuthorize` から呼ばれる `TenantAccessEvaluator` は、`workspaces` / `projects` をトランザクション外で検索していた。`RlsConnectionInterceptor` は `@Transactional` 境界でしか `app.current_org_id` を設定しない。そのため api_worker 接続では RLS（`workspaces_api_worker_policy`）が自組織の行すら隠し、所属判定は常に false になっていた。さらに、その false が `orgTenantAffiliationCache` にキャッシュされていた。

## 決定

**`TenantAccessEvaluator` にクラス単位で `@Transactional(readOnly = true)` を付ける。**

あわせて、認可チェックが無かった `PATCH /subscription` に `canAccessTenant` による `@PreAuthorize` を付与する。

## 理由

### RLS の文脈設定を一か所に保つ

`@PreAuthorize` は呼び出し元にトランザクションが無い。評価器の中だけ `@GlobalAccess`（RLSバイパス）にする案もあったが、所属判定のためにRLSを外すのは本末転倒になる。トランザクション境界を張れば、既存の `RlsConnectionInterceptor` がJWT由来の組織IDで `app.current_org_id` をセットする。RLSを効かせたまま判定でき、他組織の行は引き続き見えない。

`@EnableTransactionManagement(order = HIGHEST_PRECEDENCE)` により、トランザクション開始後に `RlsConnectionInterceptor`（`LOWEST_PRECEDENCE`）が走る順序は保証されている。

### 切替APIの認可漏れを塞ぐ

`PATCH /subscription` は認証さえ通れば、任意のワークスペースIDのプランを変更できた。取得APIと同じ判定を付け、自組織のADMINだけが変更できるようにした。

## 結果

- ワークスペース取得・ブランディング取得が所属組織のユーザーで200を返すようになった。
- プラン切替が料金ページと解析結果画面（Pro限定バナーの出し分け）に反映されるようになった。
- 他組織のワークスペースへのプラン切替は403になる。
- 回帰テスト `TenantAccessEvaluatorRlsIntegrationTest` を追加した（api_worker 接続・RLS有効で所属判定を検証する）。

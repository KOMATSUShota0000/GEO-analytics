# ADR-083: 解析枠の大きさは、ジョブと同じ読み方でプランを読んで決める

## 日付
2026-09-29

## 状況

ワークスペースが PRO なのに、2回目の解析で「解析枠の残高が不足しています。回復まで約23時間56分です。」と出て解析できなくなった（#193）。

解析枠は Bucket4j のトークンの入れ物で、ワークスペースごとに1つある。大きさは「プランの1日の上限 × 10トークン」。解析を投入するたびに「質問の数 × 10トークン」を消費する。入れ物はメモリ上（Caffeine）にあり、アプリの再起動とプラン切替で作り直される。

入れ物の大きさを決める `PlanBasedQuotaManager.configurationForWorkspace` と、枠切れの返答に載せるプランを読む `resolveWorkspacePlan` は、どちらも `workspaceRepository.findById` を `@Transactional` の外から呼んでいた。

- `RlsConnectionInterceptor` が RLS 用の組織ID（`app.current_org_id`）を接続に渡すのは、`@Transactional` の呼び出しのときだけ（ADR-042）
- `TenantPlanScope.executeWithTenant` はアプリ内のテナント情報を束縛するだけで、DB の接続には渡さない
- そのため `api_worker` の接続ではワークスペースが0件に見え、`.orElse(SubscriptionPlan.STANDARD)` によって**エラーも出さずに**どのプランでも STANDARD の大きさになっていた

一方、ジョブのプランは `JobController` が `WorkspacePlanResolver.resolvePlan` で読んでいて、正しく PRO になる。同じ解析の中で、ジョブは PRO（質問10件）、枠は STANDARD（100トークン＝質問10件分）と食い違い、1回の解析で1日分の枠が尽きていた。

| プラン | 1回の解析で使う質問 | 本来の1日の枠 | 直す前の枠 | 1日に解析できた回数 |
|---|---|---|---|---|
| STANDARD | 3件 | 10件 | 10件 | 3回（正しい） |
| PRO | 10件 | 100件 | 10件 | 1回 |
| EXPERT | 30件 | 200件 | 10件 | 0回 |

この不具合は 2026-05-26 からある。開発中は再起動が多く、PRO で同じ起動中に2回続けて解析したのが今回初めてだったため、表に出なかった。`SubscriptionIntegrationTest` は管理者接続（RLS が効かない）で動くので、テストでも見つからなかった。

## 決定

- `resolveWorkspacePlan` と `configurationForWorkspace` は、`WorkspacePlanResolver.resolvePlan` でプランを読む
- `PlanBasedQuotaManager` から `WorkspaceRepository` と `executeWithTenant` を外す
- 回帰テスト `PlanBasedQuotaManagerRlsIntegrationTest` を、RLS が効く接続（`PostgresTestBase`）で書く

## 理由

- **ジョブのプランと枠のプランを同じ読み方にそろえるため。** 読み方が2通りあると、今回のように片方だけが黙って別の答えを返したとき、1つの解析の中で食い違う
- `WorkspacePlanResolver` は `@GlobalAccess` 付きで、RLS を通らない `batchJdbcTemplate` で読む。ワークスペースIDはジョブから取った値で、呼び出し元ですでにテナントの確認を通っている
- ADR-042 や #174 のように、読み出しを `@Transactional(readOnly = true)` のメソッドへ分ける案もあった。その場合も動くが、同じクラスの中から呼ぶとプロキシを通らないため別のクラスが1つ増え、プランの読み方も2通りのまま残る。すでにジョブ側で使われている `WorkspacePlanResolver` を使えば、新しいクラスを作らずに1通りにできる

## 結果

- PRO の枠は1000トークン（質問100件分）、EXPERT は2000トークンで作られる。枠切れの返答の `plan_name` も本当のプランになる
- `PlanBasedQuotaManagerRlsIntegrationTest` の3件（PRO の枠の大きさ・EXPERT で1回解析できること・返答のプラン）は、直す前のコードでは3件とも失敗した（100トークン・STANDARD・消費できない）。直した後は通る
- `WorkspacePlanResolver` も、行が見つからないときは STANDARD を返す。今回は RLS を通らないので見つからないことは起きないが、「見えないのに黙って STANDARD」という形そのものは残っている
- `configurationForWorkspace` と `resolveWorkspacePlan` の中にあったオーナーの理解メモ2か所は、説明していたコードと一緒に消した（オーナー確認済み）
- 調べる途中で、同じ形の疑いをもう1か所見つけた。`ScheduledProjectAuditService.executeMonthlyAuditForProject` は `@Transactional` なしで `projectRepository.findById` を呼んでいるため、定期監査はプロジェクトが見えずに黙って終わっている可能性がある。本 ADR の範囲外として別に扱う

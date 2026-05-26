# ADR-003: 技術的負債6項目の修正（並列ストリーム排除・クレジットロック撤去・孤児予約回収・例外握り潰し修正・stdout汚染除去）

## 日付

2026-05-16

## 状況

包括スキャンと秘書の実コード検証により、機能の振る舞いには影響しないが規約違反・実害リスクを持つ技術的負債が6項目検出された。いずれも純粋なリファクタリング＋防御的改善＋回収機構追加で健全化する。クレジット系（Sprint 2〜4）はコアバリュー「クレジット厳密性／二重課金防止」に直結するため、DB 層の排他担保を Read で確認した上で着手した。

1. **C1**: `EntityResolutionService.resolveImmediateScored` の `IntStream.range(0,n).parallel().forEach(...)`（共有 ForkJoinPool 汚染）
2. **H2**: `CreditReservationAspect` の `ReentrantLock vaultGate`（シングルトン Aspect の単一ロックで全テナントのクレジット操作を直列化）
3. **H1/M5**: `CreditVaultService.findStaleReservations` が実装済みだが呼び出し元ゼロ（孤児 RESERVE 行のクレジット永久凍結リスク）
4. **H5**: `CreditReservationAspect` の finally 内 `refundUnderLock` 例外が元の業務例外を握り潰す
5. **H3**: `BatchPersistenceService` の `catch (JsonProcessingException ignored) {}` 2箇所（JSON 復元失敗の完全無視）
6. **M2**: `LocalWormAuditAdapter` の `System.out.println(json)`（本番 stdout 汚染）

## 決定

### Sprint 1: C1 — 並列ストリームの排除

`resolveImmediateScored` の `IntStream.range(0,n).parallel().forEach(...)` を逐次 `for` ループに置換。`ctx`（`RESOLUTION_SCOPE.get()`）の取得位置は並列化の外のまま維持。未使用となった `java.util.stream.IntStream` import を削除。L77 の `CompletableFuture.runAsync(...).join()`（1ms park, virtualYieldExecutor）は意図不明のためスコープ外として温存。

### Sprint 2: H2 — vaultGate 撤去

`ReentrantLock vaultGate` および `reserveUnderLock` / `settleUnderLock` / `refundUnderLock` を全廃し、`creditVaultService.reserve/settle/refund` の直接呼び出しに置換。`java.util.concurrent.locks.ReentrantLock` import を削除。

### Sprint 3: H1/M5 — 孤児 RESERVE 回収スケジューラの追加

新規 `infrastructure/scheduler/StaleReservationSweeper` を作成。`MonthlyAuditScheduler` のパターン（`@Scheduled` + `TenantPlanScope`）を踏襲。

- **実行間隔**: 毎時0分（`cron = "0 0 * * * *"`, zone `Asia/Tokyo`）。
- **cutoff**: 60分（`now().minusMinutes(60)`）。
- **テナントスコープ確立**: `findStaleReservations` が返す各 `WalletTransactionEntity` の `getOrganizationId()` を用い、`TenantPlanScope.executeWithTenantOrganizationAndPlan(WORKSPACE_ID, organizationId, STANDARD, () -> creditVaultService.refund(reservationId))` で行ごとにスコープを確立してから `refund` を呼ぶ。`refund` は `requireOrgId()`（`TenantContextHolder`）に依存し、かつ `findByIdAndOrganizationIdAndTransactionType` で対象行の所属組織を再検証するため、行の `organizationId` でスコープを張る必要がある。`tenantId` は `MonthlyAuditScheduler` と同じく `DefaultTenantIds.WORKSPACE_ID`、プラン値は返金処理で未使用のため `STANDARD` を指定。
- **二重返金防止**: 走査〜返金の間にジョブが完了し子行が生成された場合、`refund` 内の `existsByParentReservationId` ガードが `IllegalStateException` を投げる。これを行単位の正常系として `catch (RuntimeException)` で握り、`warn` ログを残して残りの行の回収を継続する。

### Sprint 4: H5 — 例外握り潰しの修正

`aroundAdvice` の finally 内 `creditVaultService.refund(reservationId)` を `try-catch (RuntimeException)` で囲み、refund 失敗時は `reservationId` を含む `warn` ログのみ出力。元の業務例外（`joinPoint.proceed()` 由来）の伝播を妨げない。回収漏れは Sprint 3 のスイーパーが後追いで救済する。

### Sprint 5: H3 — JSON 握り潰しの修正

`BatchPersistenceService` に SLF4J `Logger`（`private static final`、コードベース既存スタイルに合わせ Lombok 不使用）を追加。`mapJobRow`（`job_recommended_actions`, `jobId=job.getId()`）と `mapAuditRow`（`recommended_actions`, `jobId=e.getJobId()`）の 2箇所の `catch` を `warn` ログ出力に変更。フィールドは復元失敗時 未設定のまま（フォールバック挙動は不変）。

### Sprint 6: M2 — stdout 汚染除去

`LocalWormAuditAdapter.export` の `System.out.println(json)` を `log.debug("WORM audit payload eventId={} json={}", event.id(), json)` に置換（監査 JSON 本体の可視性を debug レベルで温存しつつ本番 stdout 汚染を排除）。クラス Javadoc の「標準出力および一時ファイル」記述も実態に合わせ修正。

## 理由

- **Sprint 1**: 件数 `immediateThreshold`（32件）以下の小規模データでは逐次が速く、`commonPool` を汚さない。`StructuredTaskScope`（`resolveDeepScored` で使用中）は小規模では起動コストが過剰。
- **Sprint 2 の安全性根拠**: `CreditVaultService.reserve/settle/refund` は全て `@Transactional` ＋ `organizationRepository.findByIdForUpdate(orgId)` による org 単位の DB 悲観行ロックを取得しており、同一組織のクレジット残高更新は DB 層で直列化される。加えて `settle` / `refund` は `existsByParentReservationId(reservationId)` で当該 RESERVE への子（SETTLE/REFUND）の存在を検査し、二重精算・二重返金を構造的に拒否する。`vaultGate` はこの DB 担保に対し冗長であり、全テナント・全組織のクレジット操作を単一プロセス内ロックで直列化してスループットを不当に低下させていた。撤去後も二重課金防止の不変条件（org 行ロック＋子存在チェック）は一切変化しない。
- **Sprint 3 の cutoff/間隔**: マルチペルソナ討論等の LLM ジョブは長時間化し得るため、推奨下限 30分 を大きく上回る 60分 を採用し、仮実行中の正常ジョブの誤返金を回避。毎時実行はクレジット凍結の解消遅延（最大約2時間 = cutoff60分 + 実行間隔最大60分）とDB負荷のバランス点。`existsByParentReservationId` の冪等ガードにより、万一 cutoff を跨いだ正常ジョブを拾っても二重返金は起こらない。
- **Sprint 4**: Java の finally セマンティクス上、finally 内の例外は try 内の例外を置換する。返金失敗で業務例外が隠蔽されると障害解析が不能になるため、業務例外の伝播を最優先し、返金失敗は `reservationId` 付き warn で手動／自動回収可能にする。
- **Sprint 5/6**: 規約（`System.out.println` / 例外握り潰し禁止、SLF4J 必須）準拠。フォールバック挙動は変えず観測性のみ付与。

## 結果

- 機能の振る舞いは全 Sprint で不変（純粋リファクタリング＋防御的改善＋回収機構追加）。
- クレジットの二重課金防止の不変条件は維持。`vaultGate` 撤去によりクレジット操作のテナント間並列性が回復。
- 孤児 RESERVE がスイーパーにより最大約2時間以内に自動返金され、クレジット永久凍結リスクが解消。
- トレードオフ: 孤児予約の解消に最大約2時間のラグが生じる（cutoff を短縮すると正常長時間ジョブ誤返金リスクが上がるため意図的に許容）。`StaleReservationSweeper` は全組織横断のグローバルクエリを毎時実行するため、`wallet_transactions` の `transaction_type` / `parent_reservation_id` / `created_at` にインデックスが無い場合は将来的なフルスキャン負荷に注意（今回のスコープ外、将来のインデックス検討事項として記録）。
- 変更ファイル: `EntityResolutionService.java`, `CreditReservationAspect.java`, `BatchPersistenceService.java`, `LocalWormAuditAdapter.java`、新規 `StaleReservationSweeper.java`。

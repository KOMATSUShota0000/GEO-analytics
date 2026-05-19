# ADR-002: アーキテクチャ違反3点の修正（並列化排除・synchronizedホットパス除去・デッドコード削除）

## 日付

2026-05-16

## 状況

オーディター監査により、Java 25 仮想スレッド環境のアーキテクチャ規約に違反する既存コードが3点検出された。いずれも機能の振る舞いには影響しないが、`.cursorrules` の絶対禁止事項に抵触しており、純粋な技術リファクタリングおよびデッドコード削除による健全化が必要となった。

1. `parallelStream` / `Arrays.parallelSort` の使用（共有 ForkJoinPool 枯渇リスク）
2. SSE ナレーションバッファ操作での `synchronized`（仮想スレッドのキャリアスレッドへのピン留め）
3. `DomainAuthorityCacheEntity` / `DomainAuthorityCacheRepository` のデッドコード化（かつ `domain_authority` は SEO 被リンク評価指標の残骸）

## 決定

### Sprint 1: 並列化の排除

- `EntityResolutionBlockingService.bucketByBlockingHash()`: `entities.parallelStream().collect(groupingByConcurrent(...))` を `entities.stream().collect(groupingBy(...))` に変更。
- `StrategyInsightService.rollupJob()`: `Arrays.parallelSort(zArr)` を `Arrays.sort(zArr)` に変更。

### Sprint 2: synchronized ホットパスの排除

- ナレーションバッファ `narrationLogBuffer` を `new ArrayList<>(64)` から `java.util.concurrent.ConcurrentLinkedDeque` に変更（生成箇所: `ProjectOnboardingService.runGeoPipeline()`）。
- パラメータ型を `List<DebateOnboardingSseEvent>` から `Deque<DebateOnboardingSseEvent>` に統一（`DebateOnboardingOrchestrator`・`ProjectOnboardingService`・`DebateMathAuditService` の全シグネチャ）。
- `synchronized (narrationLogBuffer)` ブロックを全て撤廃（3ファイル4箇所）。
- キャップ制御の先頭削除を `remove(0)` から `pollFirst()` に変更し、FIFO 順序・上限・要素内容を完全維持。

### Sprint 3: DomainAuthorityCache デッドコード削除

- `DomainAuthorityCacheEntity.java` と `DomainAuthorityCacheRepository.java` を削除。
- Flyway マイグレーション `V126__drop_domain_authority_cache.sql` を新規作成し `DROP TABLE IF EXISTS public.domain_authority_cache;` を実行。

## 理由

### 並列化を排除した根拠（Sprint 1）

- **`bucketByBlockingHash`**: 唯一の本番経路は `EntityResolutionService.bucketEntities()` で、名寄せ対象エンティティは1解析あたり数十〜数百件規模。`blockingHashSha256` は軽量な SHA-256 計算（CPU-bound・短時間）であり、この規模では `parallelStream` の分割・集約オーバーヘッドが逐次処理を上回る。さらに共有 ForkJoinPool（commonPool）を消費すると、仮想スレッド上で動く他の処理と競合し ForkJoinPool 枯渇を招く。`StructuredTaskScope` での明示分割も検討したが、数千件以上を扱う呼び出しが存在しないため不要と判断し、最小コストの逐次 `stream()` を採用した。
- **`Arrays.parallelSort`**: `zArr` は1ジョブ分の監査履歴 Z スコア（`rollupJob` の呼び出し元はいずれも単一ジョブの結果リスト）であり、配列サイズはクエリ数程度の小配列。小配列では逐次 `Arrays.sort` が並列ソートより高速で、かつ共有 ForkJoinPool を汚さない。
- `groupingByConcurrent` → `groupingBy` の置換は、生成される Map の内容（キー・値・各バケットの要素集合）を変えない。並列収集時のバケット内順序は元々非決定的であり、振る舞いの差異は生じない。

### ConcurrentLinkedDeque を第一候補とした根拠（Sprint 2）

- `ConcurrentLinkedDeque` はロックフリー（CAS ベース）であり、`synchronized` を完全に撤廃できるため、仮想スレッドのキャリアスレッドへのピン留めを根本的に解消する。`ReentrantLock` への単純置換よりもピン留めリスク・競合の両面で優れる。
- イテレータは弱整合性（weakly consistent）で `ConcurrentModificationException` を発生させず、`narrationEventsToJsonMaps` の走査を無ロックで安全化できる。挿入順も維持される。
- `pollFirst()` は旧 `remove(0)`（List 先頭削除）と同一の FIFO 先頭削除挙動を提供し、`MAX_NARRATION_LOG_BUFFER_ENTRIES` の上限・順序・要素内容を完全に維持する。
- キャップ適用と追加が単一ロック下で原子的に行われなくなるが、元々 `enforceNarrationLogBufferCap` は追加直前のベストエフォート上限制御であり、ソフトバウンドとしての意味は変わらない。バッファは監査ログ用途で厳密な上限保証は不要。

### テーブル削除の根拠（Sprint 3）

- プロジェクト全体を `DomainAuthorityCacheEntity` / `DomainAuthorityCacheRepository` / `domain_authority_cache` で Grep した結果、参照は当該2ファイル相互と V111 マイグレーションのみ。サービス層・コントローラ・テストからの参照はゼロで、完全なデッドコードと確認できた。
- `domain_authority`（ドメインオーソリティ）は被リンク評価に基づく SEO 指標であり、LLM の引用判断には無関係。組織ルールの「❌削除: 被リンク解析・ドメインオーソリティ」に該当する GEO 残骸。
- V111 を Read した結果、`domain_authority_cache` には RLS ポリシーは付与されておらず、UNIQUE 制約とテーブル GRANT のみ。これらはテーブル DROP で連動削除されるため追加の明示的削除文は不要。

## 結果

- Sprint 1: 振る舞い不変。共有 ForkJoinPool への依存が消え、小データ規模での処理が逐次化されオーバーヘッドが減少。
- Sprint 2: 振る舞い不変（バッファ上限・順序・要素内容を維持）。SSE ナレーション経路から `synchronized` が消滅し、仮想スレッドのピン留めが解消。トレードオフとして `ConcurrentLinkedDeque.size()` は O(n) だが、キャップ（小さい固定上限）以下でのみ呼ばれるため実コストは無視できる。
- Sprint 3: 未使用テーブルとエンティティ/リポジトリ2クラスが削除され、SEO 残骸が1点除去された。`V126` 適用済み環境では `domain_authority_cache` テーブルが消える。既存データは利用箇所が無かったため業務影響なし。
- 全スプリントを通じて機能の振る舞いは変えていない（純粋なリファクタリング＋デッドコード削除）。

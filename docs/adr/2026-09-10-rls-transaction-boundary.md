# ADR-042: RLS のトランザクション境界と、死蔵コードを配線する際の手順

## 日付
2026-09-10

## 状況

死蔵コードを配線する作業（ADR-040 / ADR-041）で、**同じ罠を2回踏んだ**。いずれも「配線したのに何も出ない」という形で現れ、例外もエラーログも出なかった。

### 罠の正体

`RlsConnectionInterceptor.aroundTransactional` は、**トランザクションがアクティブなときにのみ** RLS の GUC を設定する。

```java
boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
if (!txActive) {
    return pjp.proceed();      // GUC を設定せずに素通し
}
```

そして GUC はトランザクションローカルに設定される。

```java
private static final String SET_ORG    = "SELECT set_config('app.current_org_id', ?, true)";
private static final String SET_TENANT = "SELECT set_config('app.current_tenant_id', ?, true)";
//                                                                                     ↑ true = トランザクションローカル
```

したがって **`@Transactional`（クラスレベル可）が無いメソッドから DB を読むと、GUC が未設定のまま RLS が評価され、行が存在しても 0 件が返る。**

### 実証

`projects` の RLS ポリシー。

```sql
-- projects_api_worker_policy
(SELECT w.organization_id FROM workspaces w WHERE w.id = projects.tenant_id LIMIT 1)
  = NULLIF(current_setting('app.current_org_id', true), '')::uuid
```

直接検証した結果。

| `app.current_org_id` | `projects` の可視行 |
|---|---|
| 設定あり | **1** |
| 設定なし | **0** |

### 踏んだ2箇所

| 箇所 | 症状 |
|---|---|
| `AiRemediationService.buildGapContexts` | `audit_rubric_results` が13行あるのに0件に見え、改善タスクが1件も生成されなかった |
| `JobBenchmarkCaptureService.capture` | `projects` が読めず `project_not_found` で静かに早期 return。5ジョブ連続でベンチマークが保存されず、基礎スコアが算出されなかった |

後者はクラスレベルの `@Transactional` が無かったのに対し、同じ読み取りを行う `JobPersistenceService` はクラスレベルに `@Transactional(readOnly = true)` を持っていたため成功していた。**この差が、原因の切り分けを一度誤らせた。**

## 決定

### 1. 死蔵コードを配線する前に、対象サービスのクラス宣言を確認する

```bash
grep -n "^@\|^public class" src/main/java/.../TargetService.java
```

`@Transactional` が無ければ、DB を読む箇所は静かに空を返す。**「配線したのに何も出ない」の第一の容疑者はこれ。**

### 2. 成否のログが無いサービスには、まず計装を入れてから動かす

`JobBenchmarkCaptureService.capture` は成否を一切ログに残さず、5ジョブ連続で静かに失敗していた。**静かに何もしないコードは、動いているように見える。**

早期 return には必ず理由つきのログを残す。

```java
if (project == null) {
    log.warn("benchmark_capture_skipped reason=project_not_found jobId={} projectId={}", jobId, projectId);
    return;
}
```

### 3. 読み取りのトランザクション境界は、LLM 呼び出しを含めない

長時間かかる外部呼び出しをトランザクション内に入れない。読み取りだけを別メソッドへ切り出して `@Transactional(readOnly = true)` を付ける。

```java
List<GapContext> contexts = self.loadGapContexts(auditHistoryId, gapCriterionIds);   // トランザクション内
List<RemediationTask> tasks = self.invokeLlmWithCreditReservation(projectId, contexts); // トランザクション外
```

## 理由

この罠が危険なのは、**失敗が成功と区別できない形で現れる**ためである。

- 例外が出ない
- 警告も出ない
- 「行が無い」と「行が見えない」が同じ結果になる
- 早期 return がログを残さなければ、痕跡すら残らない

本コードベースには「起動から一度も実行されていないコード」が多数存在する。それらを配線する作業は今後も続くため（#83 の子 issue 群）、規則として明文化する。

## 結果

- `AiRemediationService` に `loadGapContexts`（`@Transactional(readOnly = true)`）を追加
- `JobBenchmarkCaptureService.capture` に `@Transactional` と成否ログを追加
- いずれも実データで復旧を確認（改善タスク10件生成、ベンチマーク保存）

**トレードオフ/申し送り**:

- 本 ADR は「非トランザクションの DB 読み取りは危険」と述べるが、`RlsConnectionInterceptor` は `@GlobalAccess` も持たず非トランザクションな**サービス層メソッド**に対しては `SecurityException` を投げる設計になっている。にもかかわらず今回の2件が例外を投げず素通ししたのは、**リポジトリを直接呼ぶ経路がアドバイスのポイントカットに一致しない**ためと考えられる。ポイントカットの範囲を広げて「静かに空を返す」を構造的に防げないかは別途検討する価値がある
- 同種の罠が他にも眠っている可能性が高い。とくに「書き込みメソッドにだけ `@Transactional` が付いていて、読み取りメソッドには付いていない」パターンに注意する

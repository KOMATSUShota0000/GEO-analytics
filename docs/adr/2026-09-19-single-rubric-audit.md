# ADR-055: 自社サイトのルーブリック監査を1解析につき1回にする

## 日付
2026-09-19

## 状況

1解析の中で、**同じ自社サイトに対するクロールと LLM 監査が2回走っていた**（#72）。

| 経路 | 何をしていたか | 結果の使い道 |
|---|---|---|
| `JobBenchmarkCaptureService.capture` | `smartDomainCrawlService.compileForAudit(targetUrl)` → `rubricAuditService.executeAudit(...)` | ベンチマーク保存（`self_rubric_audit_json` / `self_crawled_page_json`） |
| `AiRubricAuditService.runMultiDomainAuditForCompletedJob` | 同じ URL に対して同じ2つを実行 | 監査結果の永続化（`audit_rubric_results`） |

同じ URL を、同じクロール処理で取り、同じ LLM 監査へ渡していた。**使い道が違うだけで、入力も呼び出しも重複**していた。

## 決定

**監査を先に実行し、その自社分のクロール結果と LLM 監査結果をベンチマーク保存へ渡して使い回す。**

- `AiRubricAuditService.runMultiDomainAuditForCompletedJob` は自社分の成果物（`SelfAuditSnapshot`）を返す
- `JobBenchmarkCaptureService.capture(jobId, snapshot)` は、渡されたものがあればクロールと監査を行わない
- 渡されなかった場合（監査が失敗した・経路が古い）は、**従来どおり自前でクロールと監査を行う**

## 理由

### 監査側を「先」にした理由

ベンチマーク保存が必要とするのは「自社サイトのクロール結果」と「ルーブリック監査の結果」で、どちらも監査側が既に作っている。逆向き（保存側の結果を監査へ渡す）にすると、監査は複数ドメインを並列に処理する設計なので、自社だけ特別扱いする分岐が増える。

### 縮退を残した理由

監査は外部サイトのクロールと LLM 呼び出しを含むため失敗しうる。失敗したときにベンチマークまで欠測すると、基礎スコアが出ずに改善タスクが表示されなくなる（ADR-041 で直した問題の再発）。**監査が失敗しても保存側は自力で動ける**状態を保つ。

### 共有状態にしなかった理由

サービスはシングルトンで、複数ジョブが同時に走る。インスタンスフィールドに直前の結果を持たせると、別のジョブの結果を掴む。戻り値で受け渡す。

## 結果

- 1解析あたり、自社サイトのクロールとルーブリック監査の LLM コールが**それぞれ2回から1回**になる。原価に直接効く。
- 使い回したかどうかはログで分かる（`benchmark_capture_reused_audit` / `benchmark_capture_audited_itself reason=no_reusable_audit`）。
- リアルタイム経路・バッチ経路（`GeminiBatchExecutorService` / `AsyncBatchService`）の3箇所すべてで同じ順序にした。

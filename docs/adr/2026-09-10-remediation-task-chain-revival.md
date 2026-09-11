# ADR-040: 改善タスク生成の連鎖の復活とギャップ定義の競合非依存化

## 日付
2026-09-10

## 状況

「改善点を根拠付きで提示する」機能は、器がすべて揃っているのに中身を作る経路が1本も繋がっていなかった。

```
RubricGapAnalysisService.identifyGaps        呼び出し元ゼロ
AiRemediationService.generateTasks           呼び出し元ゼロ
RemediationTaskBoard（UI パネル）             実装済み・データが来ず非表示
audit_histories.job_recommended_actions      誰も書かない

実際に UI に出ていたもの → rollupJobFromTemplate（テンプレート文言）
```

さらに、単純に配線しても1件も生成されないことが判明した。`identifyGaps` のギャップ判定が撤去済みの競合機能に依存していたためである。

```java
RubricVerdictStatus selfStatus = entry.getValue();
if (selfStatus != NO && selfStatus != PARTIAL) continue;
LinkedHashSet<RubricVerdictStatus> compSet = competitorVerdicts.get(entry.getKey());
if (compSet == null || compSet.isEmpty()) continue;   // 競合行が無ければギャップにならない
if (compSet.contains(YES)) gaps.add(entry.getKey());
```

競合サイトのルーブリック監査は Sprint C5（#36）で撤去済みで、非自社行を作る経路が存在しない。実データでも `audit_rubric_results` は全13行が `is_self = true` だった。したがって `identifyGaps` は常に空を返し、`generateTasks` は LLM を呼ばずに `List.of()` を返していた。

## 決定

### 1. ギャップの定義を「自社が NO / PARTIAL」の絶対条件へ変更する

競合データを必要としない形にする。`MAX_GAPS = 5` の上限はそのまま維持する。

### 2. 競合行は削除せず、順序付けの材料として残す

```java
gaps.sort(Comparator.comparingInt(criterion -> gapPriority(criterion, selfVerdicts, competitorVerdicts)));

// 競合が達成している基準を最優先し、同条件なら未達(NO)を部分達成(PARTIAL)より前に置く
return (competitorAchieved ? 0 : 2) + (selfUnmet ? 0 : 1);
```

### 3. 呼び出し順の制約を1箇所へ閉じ込める

`RemediationTaskOrchestrationService` を新設し、リアルタイム経路からは1行で起動する。

```java
aiRubricAuditService.runMultiDomainAuditForCompletedJob(jobId);
// ルーブリック監査の行が永続化された後でしかギャップを判定できないため、この順序を保つこと。
remediationTaskOrchestrationService.generateForCompletedJob(jobId);
```

### 4. 失敗時は解析を落とさず縮退する

改善タスクの生成に失敗しても解析は COMPLETED まで進む。ベンチマーク取得・ルーブリック監査と同じ方針。

## 理由

### 競合前提を消し込まず、拡張点として残す理由

競合サイトのルーブリック監査は将来復活させる予定がある（#76）。そのとき「競合は達成しているが自社は未達」という**相対的なギャップ**は、絶対評価より提案の説得力が高い。代理店が顧客へ見せる資料としての価値が上がる。

競合前提のロジックを削除してしまうと復活時に書き直しになる。順序付けの材料として残せば、`compSet` の有無がそのまま優先度に効くため、**この構造のまま相対ギャップを取り戻せる**。

`audit_rubric_results.is_self` カラムとインデックスも同じ理由で残す（現在は全行 true で意味を持たないが、復活時に必要）。

### 呼び出し順の制約を閉じ込める理由

ギャップ判定はルーブリック監査の行が永続化された後でしか動けない。この制約を解析フローに直接書くと、フローを読む人が制約に気づけない。1つのサービスに閉じ込め、フロー側は1行にする。

## 実装中に判明した2件のバグ

いずれも**一度も実行されたことがないコード**だったため露見していなかった。

### 1. RLS のトランザクション境界

`buildGapContexts` が非トランザクションで `audit_rubric_results` を読み、RLS の GUC が未設定のまま全行を弾かれていた。行は13件存在したが0件に見えていた。例外も警告も出ない。

読み取りのみを `@Transactional(readOnly = true)` へ閉じ込め、長時間かかる LLM 呼び出しはトランザクション外に残した。詳細は ADR-042。

### 2. `ChatRequest` にユーザーメッセージが無い

```
HTTP error (400): "* GenerateContentRequest.contents: contents is not specified"
```

`RemediationTaskPrompts` がギャップ本文までシステム指示へ押し込む作りで、ユーザーターンが存在しなかった。Gemini は contents が空のリクエストを拒否する。

`systemPrompt(gaps)` を `systemInstruction()` と `userPayload(gaps)` に分割し、他プロンプト（`RubricAuditPrompts`）と同じ「ルールはシステム、対象データはユーザーターン」の構成へ揃えた。

出力スキーマ（`RemediationTaskOutputSchema`）は `AiConfig:190` でモデル bean 側に設定済みであり、こちらは正しく配線されていた。

## 結果

実データで検証した（ガスト / `LOCAL_STORE` / PRO）。

```
remediation_tasks_generated gaps=5 tasks=10
audit_histories.job_recommended_actions … 10件
GET /api/v1/jobs/{id}/analysis … remediation_tasks 10件
```

生成された内容は `### なぜ必要か` と `### 実行手順` を持つ Markdown で、タイトルに根拠となったルーブリック基準名が入る（`DIRECT_ANSWER_FIRST` / `ATOMIC_FACTS` / `VERIFIABLE_AUTHORITY`）。

`RubricGapAnalysisServiceTest` を新設（5件）。`./mvnw clean test` 246件 PASS。

原価は1解析あたり LLM +1コール、チケット +200。ギャップが0件なら実行されないため、達成度の高いサイトでは発生しない。

**トレードオフ/申し送り**:

- 生成される内容が**そのサイト固有の観測を引用していない**。`GapContext.selfEvidence` としてプロンプトには渡っているのに、出力側に根拠を書かせる指示とフィールドが無い。#79 で `evidence` フィールドを追加する
- 改善タスクを作る仕組みが3系統ある（`prioritizedTasks` / `RemediationTask` / `roadmap_items`）。本 ADR は `RemediationTask` を配線したが、残り2つの取捨は未決。`roadmap_items` は重複ではなく別の deliverable（ロードマップ）であり #77 で扱う

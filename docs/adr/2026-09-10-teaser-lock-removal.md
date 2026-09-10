# ADR-041: 改善タスクの Teaser ロック撤去とベンチマーク保存の修復

## 日付
2026-09-10

## 状況

改善タスクを配線した（ADR-040）直後、API 応答では全10件が伏せられていた。

```json
{"title": "冒頭への直接的回答（アンサーボックス）の追記",
 "content": "🔒 基礎スコア 80点 到達で解放されます", "level": 3}
```

調査により2つの独立した問題が判明した。

### 1. 基礎スコアがそもそも算出されていなかった

`JobAnalysisBenchmarkAssembler` は `jobs.self_rubric_audit_json` を入力にする。この列が NULL だと早期 return し `factBasedScore` が null になり、`RemediationTaskResponseMask` は null のとき**無条件にマスク**する。

実データでは**全5ジョブで NULL** だった。書き込むはずの `JobBenchmarkCaptureService.capture` は、ログに何の痕跡も残していなかった（ログ全体で "benchmark" の文字列が0件）。例外で落ちたのではなく、静かに何もしていなかった。

計装を入れて特定した原因は、**クラスレベルの `@Transactional` の欠落**だった。

```java
@Service
@Transactional(readOnly = true)     // JobPersistenceService には付いている
public class JobPersistenceService { ... }

@Service                            // JobBenchmarkCaptureService には無い
public class JobBenchmarkCaptureService { ... }
```

RLS の GUC が未設定のまま `projects` を読み、ポリシーに弾かれて `project_not_found` で早期 return していた。詳細は ADR-042。

```
@Transactional の欠落 → GUC 未設定 → projects が RLS に弾かれる
  → project_not_found で静かに早期 return（例外もエラーログもなし）
    → jobs.self_rubric_audit_json が永久に NULL → 基礎スコアが算出されない
      → 無条件マスク → 改善タスクが常時ロック
```

### 2. ロックの向きが逆立ちしていた

閾値は優先度から機械的に決まる。

```java
case B -> new RemediationPriorityLevel(1, 0.0);   // 常に見える
case A -> new RemediationPriorityLevel(2, 60.0);
case S -> new RemediationPriorityLevel(3, 80.0);
```

**優先度が高い（＝真っ先にやるべき）タスクほど、高いスコアを要求される。**

```
S級タスクを読みたい  →  80点必要
80点にしたい        →  S級タスクを実行する必要がある
                        ↑ しかし読めない
```

助言を最も必要とする顧客にだけ助言が届かない構造だった。

## 決定

### 1. `JobBenchmarkCaptureService.capture` に `@Transactional` を追加する

あわせて成否のログを恒久的に残す（`benchmark_capture_started` / `_skipped reason=...` / `_persisted`）。**静かに何もしない状態を二度と作らないため。**

### 2. Teaser ロックを撤去する

`JobController` からマスクの適用を外し、`RemediationTaskResponseMask` を削除する。

### 3. ゲート方式の再設計は延期する

`RemediationPriorityLevel` の対応表は再利用のため残す。方式（プラン別 / 件数別 / なし）は #84 で決める。

## 理由

### ロックを撤去する理由

**循環している。** 上記のとおり、S級タスクを読むには80点が必要で、80点にするにはS級タスクの実行が必要。

**アップセル誘導として機能していない。** CLAUDE.md はプロダクトの核として「Teaser UI によるProプランへのアップセル誘導」を挙げているが、実装は**スコア基準であってプラン基準ではない**。課金しても解放されない。

**閾値へ到達できない。** 修復後の実測（ガスト。全国チェーンのファミリーレストラン）。

| 要素 | 実測 | 満点 |
|---|---|---|
| AI ルーブリック | 7.5 | 50 |
| MEO トラスト | **25.0** | 25 ← クチコミ450件・3.4星で満点 |
| 機械可読性 | 0.0 | 25 |
| **基礎スコア** | **32.5** | 100 |

MEO が満点でも 32.5点。全国チェーンの公式サイトでこれなら、**現実的にこの閾値へ到達できる顧客はほぼ存在しない**。

**レポートの価値を削っている。** 本プロダクトは代理店が自分の顧客への提案に使う。南京錠だらけのレポートはそのまま提案資料として出せない。

### ゲート方式の再設計を延期する理由

閾値は基礎スコアの分布に依存し、その基礎スコアは ADR-039 の測定基盤是正でこれから変わる。**今チューニングしても、スコアが変われば作り直しになる。**

## 結果

修復により、5ジョブ連続で NULL だったベンチマークが初めて保存された。

```
benchmark_capture_persisted jobId=4ed60223-... meoCount=450 meoStars=3.4
```

`./mvnw clean test` 246件 PASS。オーナーが画面で改善タスク10件の表示を確認した。

**トレードオフ/申し送り**:

- `RemediationTaskResponse` の `level` / `requiredScoreThreshold` は**現在どこからも強制されない情報値**になった。ゲート方式が決まった時点で、使うか削るかを判断する
- 優先度→閾値の対応表が**フロントにも重複している**（`frontend/src/types/analysis.ts:839-846`）。同じルールが2言語に別々に書かれており、片方だけ変えるとずれる。再設計時にどちらを単一情報源にするか決めること
- 非地域業種は MEO 枠25点を構造的に持たない。権威軸では `authorityLocalMeoSub` が非地域業種の枠をゼロにして中核へ再配分しているが、**基礎スコア側には同じ手当てが無い**。#84 で扱う

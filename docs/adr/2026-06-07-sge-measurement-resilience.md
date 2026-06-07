# ADR-037: SoM/SGE測定のSerpAPI失敗を非致命化（解析を完走させる）

## 日付
2026-06-07

## 状況
実機解析（おにぎりこんが・jobId 9d50c19d…）が **FAILED** になった。原因はSoM/SGE測定の SerpAPI 呼び出しの接続タイムアウト:

```
AsyncSgeMeasurementService.measureSgeForJob → GeoCompetitorSearchAdapter.checkSgeMention
→ GET https://serpapi.com/search.json → java.net.SocketTimeoutException: Connect timed out
```

`measureSgeForJob` は `StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow()` を使っており、**1クエリでもSerpAPI呼び出しが失敗すると `scope.join()` が例外→ジョブ全体を FAILED** にしていた。SerpAPIの一時的な接続失敗（ネットワーク起因）で、コンテンツ/技術/権威/エビデンスを含む解析全体が無に帰す耐障害性の欠陥。

なお、SerpAPIキー未設定時は「空プレースホルダ（mentioned=false/count=0）に降格して継続」する処理が既に存在していた（同じ思想を呼び出し失敗時に適用していなかっただけ）。

## 決定
SoM/SGE測定を**個別クエリ単位でグレースフルに降格**させ、ジョブ全体を落とさない。

- スコアの Joiner を `awaitAllSuccessfulOrThrow()` → **`awaitAll()`** に変更（全サブタスクの完了を待つ・個別失敗で例外を投げない）。
- 各サブタスクの `state()` を確認し、`SUCCESS` 以外（FAILED/UNAVAILABLE）は **空SGE結果（"{}" / mentioned=false / count=0）を保存して継続**。`log.warn` で降格を記録。
- これによりSerpAPIが一時不通でも、当該クエリのSoMが0に降格するだけで、**コンテンツの充実度・技術・権威・エビデンス（ADR-035）・改善タスク等の解析は完走**する。
- 2-arg `measureSgeForJob` は3-argに委譲しているため、リアルタイム/バッチ両経路がこの修正でカバーされる。

## 理由
- 外部API（SerpAPI）の一時的失敗は不可避。単一の接続タイムアウトで1解析（=1チケット）が丸ごと失敗するのはプロダクト価値・信頼性として許容できない。
- 既存の「キー未設定→空降格」と同じ思想で一貫性がある。
- スコア非依存の content/technical/evidence は SerpAPI と無関係に届けられる。

## 結果
- SerpAPI接続失敗時もジョブは COMPLETED となり、SoM/AI認識は0降格・他軸は通常どおり。`mvn test` 緑（コンパイル緑・StructuredTaskScope awaitAll/Subtask.State API確認）。
- 申し送り（pending-implementations）: ①SerpAPI失敗時のリトライ（指数バックオフ）で降格頻度を下げる ②権威軸(第三者言及)のSerpAPI経路も同様の降格になっているか別途確認 ③根本のネットワーク到達性（serpapi.com 接続タイムアウト）は環境要因の可能性—再解析で回復することが多い ④1クエリ問題（信頼性の土台）。

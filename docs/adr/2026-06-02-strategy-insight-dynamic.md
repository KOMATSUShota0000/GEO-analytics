# ADR-018: 戦略診断の定型文を撲滅し SoM 絶対値ベースの動的診断へ

## 日付

2026-06-02

## 状況

「ジョブ全体の戦略診断」および各クエリの「戦略診断・推奨」が、解析内容に関わらず同じ4文（市場の支配者／有力な選択肢／レッドオーシャン／死角）のいずれかを返す**定型文**になっていた。

真因は `StrategyInsightService.fromModifiedZ(z)` が **modified Z-score（ジョブ内相対）だけで4分類**していたこと。単一クエリ（n=1）では分散が取れず `modifiedZ=0` に固定され、SoM が非ゼロ（実測 12点）でも必ず「REDOCEAN（中央値レッドオーシャン）」になる。実ジョブ（himawari, SoM=12）で、市場ポジション診断（Tier）は SoM 絶対値で「Challenger」と正しく出る一方、戦略診断は「中央値に密集・レッドオーシャン」と**矛盾**していた。Tier は SoM 絶対値軸、戦略診断は Z-score 相対軸という**二軸の不一致**が根本。

オーナー要望: 「定型文はやめてほしい。定型文にするくらいなら項目を削除した方がいい」。

## 決定

戦略診断を **SoM 絶対値ベース＋実測値の動的埋め込み**へ転換する。

### 新メソッド `describeForQuery(Double som, Integer aiPos)`

- SoM 帯（Tier 統一しきい値 **6 / 16 / 31**）で基本方針（ティア名・次の一手・推奨アクション）を選ぶ。
- そこに**その解析の実測値**を `String.format` で埋め込む:
  - SoM 点（`%.1f`）、ティア名、引用順位（`%d番目の推奨として引用` / `未掲載`）。
- SoM が動けば点数・ティア・次の一手が変わるため、**解析ごとに異なる文**になる（定型文の撲滅）。
- センチメントは従来どおりスコア・診断に乗せない（可視性とセンチメントの分離・ADR-017 と一貫）。

### `describeJobRollup(...)`（ジョブ全体）

クエリ数・SoM 中央値・最高/最低を文に埋め込む（例: 「3件のクエリを解析。SoM中央値は12.0点（最高20.0／最低8.0）…」）。

### 差し替えた経路

- `JobPersistenceService`（即時並列経路）: `fromModifiedZ` → `describeForQuery(somScore, aiCitationPosition)`。
- `GeminiResultProcessor`（バッチ経路）: `fromModifiedZ` → `describeForQuery(somScore, m.aiCitationPosition())`。
- `StrategyInsightService.rollupJobFromTemplate`: 改Z' 中央値→`fromModifiedZ` を、SoM 中央値→`describeJobRollup` へ。
- `resolveForAudit`（表示フォールバック）: 保存済み diag が無い場合 `describeForQuery` を使う。

### 温存・存置

- **改Z'（modified Z-score）の数値表示**は維持（`StrategyInsight.representativeModifiedZ` に `medZ` を格納）。相対指標としての価値は残し、診断文だけ絶対値ベースにした。
- `fromModifiedZ` / `fromVisibilityStage` / `MSG_*` は **Pro 経路**（`DebateAdviceGeneratorService` の AI 議論ヒント、`GapAnalysisService`、`keywordInsightRelative`、`buildGapAnalystFullPrompt`）でまだ使用されるため**存置**。Pro は LLM 駆動で元々動的なため定型文問題は出にくい。

## 理由

- 定型文は「分析している感」だけで中身が無く、オーナーの不信を招く。実測値を埋め込めば、同じ帯でも数値・引用順位で文が変わり、KPI として意味を持つ。
- Tier（SoM 絶対値）と戦略診断を**同一軸**に揃え、画面内の矛盾を解消。
- 単一クエリでも破綻しない（modified Z-score の n=1 ゼロ固定問題を回避）。
- PWIM（ADR-017）で SoM を絶対評価に転換した方針と一貫する。

## 結果

- 各クエリ・ジョブ全体とも、解析ごとに固有の診断文を返す（例: himawari SoM=12 →「現在のSoM可視性は12.0点（Challenger（チャレンジャー）ティア）。順位付き推奨リストには未掲載です。…」）。
- テスト追加（`StrategyInsightServiceTest`）: 実測値埋め込み・SoM 差で文が変わる・引用順位反映・rollup のクエリ数埋め込み。既存テストは `som` 無し行のためステージフォールバック経路で不変。
- **未コミット**。`./mvnw clean test` は未実施＝オーナー手動実行で確認が必要。
- **残課題**: Pro 経路（`DebateAdviceGeneratorService` のヒント生成・`GapAnalysisService`）はまだ `fromModifiedZ` を使う。将来 `describeForQuery` 系へ統一する候補。文言は実測値ベースだが帯ごとの骨格は固定なので、さらなる具体化（AI 生成の Standard 解放等）は別途プラン設計判断。

# ADR-062: マイノリティ・レポートを解析側にも配線する

## 日付
2026-09-20

## 状況

`MinorityReport`（合意に至らなかったが捨てるに惜しい尖った提案）は**オンボーディング議論にしか存在しなかった**（#80）。

| 層 | オンボーディング | 解析 |
|---|---|---|
| 型 | `MinorityReport` | 同じ型が使える |
| 保存 | `projects.minority_reports`（V110） | **無し** |
| API | `ProjectContextResponse.minorityReports` | **無し** |
| 画面 | `GeoOnboardingView` で編集可能 | **無し** |

CLAUDE.md がプロダクトの核の第一項に挙げる `DIRECTOR` の役割は「盤石な合意案**とマイノリティ・レポート**へ構造化」だが、解析ごとの議論（#73 で全プラン実行に確定）はマイノリティ・レポートを一切出力していなかった。合意案（`recommended_actions` 3件）に丸められた時点で、尖った案は議論のたびに捨てられていた。

## 決定

### 1. DIRECTOR の出力スキーマに `minority_reports` を足す

0〜2件。`insight` / `conflict_reason`（採らなかった理由）/ `evidence`（入力のどこに拠り所があるか）。**無ければ空配列**とプロンプトで明示し、無理な水増しをさせない。`evidence` に入力外を書くことは禁止（`.cursorrules` 9節・Cite Before You Speak）。

### 2. 解析専用の DIRECTOR ビーンを分ける（作業中に見つかった欠陥の是正）

配線を調べる過程で、**解析側の DIRECTOR 呼び出しがオンボーディング用の構造化出力スキーマに縛られていた**ことが判明した。

```java
// AiConfig: DIRECTOR ビーンはモデル側に書式を固定していた
.responseFormat(DebateDirectorOutputSchema.debateDirectorResponseFormat())  // debate_director_onboarding
```

LangChain4j 0.36.2 の `GoogleAiGeminiChatModel.chat(ChatRequest)` は
`getOrDefault(chatRequest.responseFormat(), this.responseFormat)` で書式を決める（バイトコードで確認）。
`DebateAdviceGeneratorService` は `ChatRequest` に書式を渡していないため、**モデル側のオンボーディング用
スキーマが常に適用**されていた。そのスキーマは `industry_type` / `extracted_strengths` / `target_audience` /
`minority_reports` を必須とし `additionalProperties=false` を課すため、解析が必要とする `diagnostic_message`
は構造上返せない。結果、解析アドバイスは

```
DIRECTOR 応答（オンボーディング形） → diagnostic_message が null
    → DebateAdviceGenerationException → テンプレフォールバック
```

を毎回たどっていた。#73（ADR-059）で「全プランで議論を自動実行」と決めた配線は正しかったが、
**成果物はテンプレに落ち続けていた**（画面上は「簡易分析モード」バッジ）。

是正: 解析用 `DebateAdviceOutputSchema`（`diagnostic_message` / `recommended_actions` / `minority_reports`）と
専用ビーン `geminiDebateAdviceDirector` を新設し、`DebateAdviceGeneratorService` をそちらへ付け替えた。
モデル・温度・タイムアウトは従来どおりで、書式だけ解析用に差し替える。
`DebateAdviceOutputSchemaTest` が「スキーマが混ざらないこと」「サービスが解析用ビーンに繋がっていること」を
リフレクションで機械的に守る。

### 3. `StrategyInsight` には足さず、別の器で運ぶ

`StrategyInsight` は16箇所で生成されており（テンプレ4分類・相対診断・ギャップ解析…）、そこに項目を足すと**議論が存在しない経路まで巻き込む**。議論の成果物は議論の経路だけが持つべきなので、次の2つの器を新設した。

```
DebateAdviceGeneratorService.JobAdvice(insight, minorityReports)
        ↓
StrategyInsightService.JobAdviceRollup(insight, source, minorityReports)
        ↓
jobs.minority_reports（V140, JSONB, DEFAULT '[]')
```

### 4. テンプレ経路では列を上書きしない

`GapAnalysisService` はテンプレフォールバック時に `null` を渡し、SQL 側は `COALESCE(?::jsonb, minority_reports)` で既存値を守る。**議論が失敗した回の空配列で、成功した回の結果を消さない**ため。

### 5. 表示は「やるべきこと」と分ける

推奨アクションと同じ見た目にすると即時着手すべき施策と誤読される。画面・PDF とも琥珀系の別ブロック（`MinorityReportPanel`）で、「合意には至らなかったが条件次第で効く可能性がある提案」と明示する。

## 却下案

| 案 | 却下理由 |
|---|---|
| `StrategyInsight` にフィールド追加 | 16箇所の生成点すべてに空配列を書かせることになり、議論を持たない経路にまで議論由来の概念が漏れる |
| `projects.minority_reports` を解析でも使い回す | オンボーディングの見立て（プロジェクト単位・ユーザー編集可）と解析ごとの議論成果は別物。上書きするとユーザーの編集内容が消える |
| 改善タスク（`RemediationTask`）に混ぜる | ADR-061 で改善タスクは「根拠付きでやるべきこと」と定義した。採らなかった案を混ぜると優先順位の意味が壊れる |

## 影響

- 新規マイグレーション `V140__jobs_minority_reports.sql`（既存ファイルは触らない）
- **議論駆動アドバイスが初めて実際に効くようになる**（従来は常にテンプレ）
- 収支面: 従来も LLM 呼び出し自体は発生していた（ペルソナ6回＋DIRECTOR は議論注入版と
  フォールバックの単発版で2回）。パースが必ず失敗するため `reserveDebateAndGenerate` は毎回
  **チケットを全額返金**しており、**支払いだけ発生して売上計上されない**状態だった。是正後は
  成功時に `settle` されるため、議論チケット（200単位）が本来の設計どおり消費される
- `JobAnalysisDetailResponse.minority_reports` が増える（追加のみ、既存フィールドは不変）
- 上限は2件・各項目200文字。表示崩れと JSONB 肥大を防ぐためサービス側で刈る

## 関連

- #80（本件）、#73（全プラン議論実行 / ADR-059）、#82（プロジェクト側レポートを解析プロンプトへ）
- ADR-061（改善タスクの根拠）、V110（`projects.minority_reports`）

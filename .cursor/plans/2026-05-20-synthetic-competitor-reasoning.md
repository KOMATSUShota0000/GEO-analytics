# 仕様書: synthetic 競合 reasoning の実生成化

- **作成**: CPO室 / 2026-05-20
- **対象スプリント**: Sprint8（dev-phase-log フェーズ8）
- **関連課題**: dev-status 懸念点#6「synthetic 競合の reasoning がダミーテキスト」

## 1. 背景・目的（What / Why）

`SyntheticSelectedCompetitorFactory` の `reasoning` は業種・商圏ラベルを差し込むだけの
**固定テンプレ文**で、3体とも実質同一。AIペルソナ議論やレポートに表示される
「なぜこの競合を選んだか」が空疎で、WOW体験の質を下げている（核①）。

合成競合が生成されるのは以下の3文脈：
1. 競合候補が抽出ゼロ（places/organics empty）
2. AIフィルタ選定が3社未満 → 不足分を padding（`padSyntheticToThree`）
3. 競合フィルタAIが例外（catch フォールバック）

現状の reasoning はこの**発生理由を一切反映しない**。

## 2. 設計（How）

### 2.1 発生理由の型
`SyntheticSelectedCompetitorFactory.SyntheticPadReason`（nested enum）：
- `NO_CANDIDATES`（候補抽出ゼロ）
- `INSUFFICIENT_REAL`（実競合が規定数未満）
- `FILTER_UNAVAILABLE`（フィルタAI一時不可）

各値は日本語の原因句（causeClause）を保持。

### 2.2 ordinal 別の参照ティア（3体を意味的に差別化）
固定テンプレではなく、序数ごとに**異なる基準点**を表現する：
| ordinal%3 | ティア名 | 説明 |
|---|---|---|
| 0 | 優位参照モデル | 当該業種で高いGEO Readinessを示す上位水準の参照点 |
| 1 | 中央値参照モデル | 業種中央値水準・AI推奨ポテンシャルの基準点 |
| 2 | 改善余地参照モデル | AI可視性ランクに改善余地が大きいベースライン参照点 |

→ ペルソナ議論が「同一文の3体」ではなく**3つの異なる基準点**を得る（実利的改善）。

### 2.3 reasoning 生成規則（誤魔化さない）
```
{area}における{industryLabel}市場の{ティア名}。{causeClause}実在競合ではなく
GEO Readiness相対評価の基準点として配置した（{ティア説明}）。
```
- area 空時は「対象商圏」にフォールバック（既存挙動踏襲）
- **合成である事実を明示**（「実在競合ではなく…基準点」）。実在を装わない

### 2.4 メソッドシグネチャ
- `singleFilterPadPlaceholder(IndustryType, String area, int ordinal, SyntheticPadReason reason)`
- `threeShortReasoningPlaceholders(IndustryType, String area, SyntheticPadReason reason)`
  （ordinal 0/1/2 で3ティアを生成）
- `synthetic=true` は維持（DTO 不変）

### 2.5 呼び出し側更新
| ファイル | 箇所 | 渡す reason |
|---|---|---|
| `CompetitorFilterService.filter` empty | NO_CANDIDATES |
| `CompetitorFilterService.filter*` catch | FILTER_UNAVAILABLE |
| `CompetitorFilterService.padSyntheticToThree` | INSUFFICIENT_REAL |
| `CompetitorFilterService.filterFromSerpOrganic` empty | NO_CANDIDATES |
| `LocalStoreStrategy`（projectId null / places empty） | NO_CANDIDATES |
| `SerpJobCompetitorExtractor`（projectId null） | NO_CANDIDATES |

## 3. 受け入れ基準（オーディター監査項目）

1. `./mvnw clean test` 全件 PASS（既存退行なし）
2. 新規 `SyntheticSelectedCompetitorFactoryTest`：
   - 3体の reasoning が相互に異なる（ティア差別化の検証）
   - reason 別に causeClause が反映される
   - area 空時フォールバック
   - 「実在競合ではなく」を含む（合成明示）
3. 旧固定テンプレ文「GEO Readiness評価用の参照モデルとして配置した。」がコードから消滅
4. `SelectedCompetitor` レコードは不変（`synthetic=true` 維持）
5. アーキ規約遵守（ThreadLocal/synchronized/parallel 不使用）
6. ADR 作成

## 4. スコープ外

- 実競合（`synthetic=false`）の reasoning は AI 生成のため対象外（変更しない）

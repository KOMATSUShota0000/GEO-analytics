# ADR-006: synthetic 競合 reasoning の実生成化

## 日付

2026-05-20

## 状況

`SyntheticSelectedCompetitorFactory` の `reasoning` は業種・商圏ラベルを差し込むだけの固定テンプレ文（「…GEO Readiness評価用の参照モデルとして配置した。」）で、3体とも実質同一だった。合成競合が生成される文脈（候補抽出ゼロ／実競合が規定数未満／フィルタAI例外）を一切反映せず、AIペルソナ議論やレポートの「選定理由」表示が空疎で WOW 体験（核①）の質を下げていた（dev-status 懸念点#6）。

## 決定

- 発生理由を表す nested enum `SyntheticPadReason`（NO_CANDIDATES / INSUFFICIENT_REAL / FILTER_UNAVAILABLE）を導入し、各値が日本語の原因句を保持
- 序数ごとに異なる参照ティア（優位／中央値／改善余地）を割り当て、3体を意味的に差別化
- `reasoning` を「{商圏}における{業種}市場の{ティア}。{原因句}実在競合ではなくGEO Readiness相対評価の基準点として配置した（{ティア説明}）。」として動的生成
- 合成である事実（「実在競合ではなく…基準点」）を常に明示し、実在競合を装わない
- 呼び出し側6箇所（CompetitorFilterService×4＋padSyntheticToThree、LocalStoreStrategy×2、SerpJobCompetitorExtractor×1）が文脈に応じた reason を渡すよう更新
- `SelectedCompetitor` レコード（`synthetic=true`）は不変

## 理由

- **誤魔化さない原則**: 実在を装う固定文ではなく、なぜ合成が必要だったか・どの基準点かを明示する方がプロダクトの信頼に資する
- **実利的改善**: 3体が同一文ではなく3つの異なる参照ティアになることで、4人ペルソナ議論が差別化された基準点を得る（核①WOW体験の質向上）
- **最小侵襲**: DTO・永続化・JSON契約は不変。生成ロジックと呼び出し側の reason 受け渡しのみの変更で退行リスクが小さい
- **純粋関数**: ファクトリは外部依存ゼロのため単体テストが容易（`SyntheticSelectedCompetitorFactoryTest`）

## 結果

- 合成競合の reasoning が文脈（業種・商圏・発生理由・ティア）を反映した実生成テキストになった
- 旧固定テンプレ文はコードベースから消滅
- 振る舞い・スキーマ・API契約に変更なし（実競合 `synthetic=false` の AI 生成 reasoning は対象外・不変）
- スコープ外: 実競合の reasoning（AI生成のため変更しない）

# ADR-021: 競合検索の職種ワード化（無関係競合の是正）

## 日付
2026-06-03

## 状況

競合抽出で対象と無関係な事業者が「実競合」として表示される問題が実ジョブで判明（訪問看護ステーションの解析に「株式会社YTC・PLUS」「LG Yokohama Innovation Center」が混入）。

根本原因: 競合パイプライン（AI属性推論 → Places/SERP検索 → AIフィルタ）は揃っているが、AI属性推論の出力 `TargetAttributes` が業種を6分類enum `IndustryType`（YMYL/LOCAL/B2B/B2C/EC/OTHER）に丸めるだけで、「訪問看護」のような具体的職種ワードを保持しない。LOCAL_STORE経路の `LocalStoreRippleSearch` は Places検索語を `区名 + IndustryType.getLabel()` で組み、YMYLの label は「YMYL分野」→ 検索語「港北区 YMYL分野」で無関係事業者が返る。候補が無関係なため後段AIフィルタも是正できない。

## 決定

AI属性推論の出力に「具体的職種ワード（categoryKeyword）」を1項目追加し、競合検索語の主語に使う。

- `TargetAttributes` に `categoryKeyword` を追加。`TargetAttributesOutputSchema`（構造化出力スキーマ）にも追加し required 化（スキーマに無いとGeminiが出力しないため必須）。
- `TargetAttributesPrompts` で「Googleマップ/Web検索で同業競合が見つかる具体的職種ワードを1つ。粗い分類語・地名・ブランド名は含めない」と指示。
- `LocalStoreRippleSearch.buildDistinctQueries`: 検索語を `categoryKeyword`（例: 訪問看護）優先で構成。空なら従来の `IndustryType.getLabel()` へフォールバック。
- `SerpJobCompetitorExtractor`: 主検索語を `categoryKeyword` 優先（無ければ `getSearchLabel` → `getLabel`）。

## 理由

- 真因は「職種情報を粗いenumに丸めて捨てている」こと。検索語に具体的職種を通すのが本質的修正。
- パイプライン（推論→検索→AIフィルタ）は既存。**追加LLM呼び出しゼロ**（既存の属性推論コールの出力を1項目増やすだけ）＝核④高利益率を死守。
- フォールバックを残し、職種ワード未取得時も従来動作で劣化しない。
- 代替案「競合機能の削除」はオーナーと協議の上、修正費用対効果が高いため不採用（ADR検討メモ: 2026-06-03 learnings）。

## 結果

- 検索語が「商圏 + 職種ワード」（例: 港北区 訪問看護）になり、業種一致の候補が上がる → AIフィルタが的確に3件選定。
- `TargetAttributes` のコンポーネントが6→7に増加（生成は Jackson 逆シリアライズのみ・`new` 呼び出し無しのため呼び出し側影響なし）。
- スキーマ required に `categoryKeyword` を追加。判断不能時は AI が null を返しフォールバックする想定。

# ADR-048: SoM の入力を Java 実測値にし、引用元の重みを SoM から外す

## 日付
2026-09-19

## 状況

SoM の計算式に渡る値が壊れていた（#60）。

### 1. 引数名と中身が食い違っていた

```java
int llmBrandPassageChars = metrics.tokenCount();                    // LLM 自己申告の「文字数」
metrics.toRawMetrics(plan, si, responseTokenLength, llmBrandPassageChars, ...);
//                                                  ↑ 受け側の引数名は nlpNounCount（＝言及回数）
```

`.cursorrules` 12節は「文書長や言及数などの確定的な物理量は AI に数えさせず、必ず Java の NLP エンジンが算出した値を渡す」と定める。これに反していた。

### 2. 単位の合わない割り算になっていた

```java
double mentionDensity = mentions / total;   // 「文字数」÷「形態素トークン数」
double countFactor = min(1.0, mentions / MENTION_COUNT_SATURATION);  // 定数は 12「回」
```

`MENTION_COUNT_SATURATION = 12` は「12回の言及で飽和」の意図だが、渡っていたのは文字数。**言及が12文字を超えた時点で常に飽和**していた（実測では 6,780 という値が入っていた）。

### 3. 引用元の重みが天井を押し下げていた

```java
mentionComponent = mentionSignal × (sourceWeight / SOURCE_WEIGHT_HIGH)   // 最大 0.2 倍
```

言及成分の最大値が 0.6 × 0.2 = **0.12** になり、どれだけ言及されても SoM が 12.0 に張り付く原因の半分を作っていた。

## 決定

### 1. 言及回数・言及文字数・トークン数は Java の実測値を使う

`BrandMentionEngine.measure`（#59）の結果を渡す。LLM 申告の `token_count` はスコアに使わず、比較のためログにだけ残す（`mention_metrics ... llmTokenCount=`）。

### 2. `sourceWeight` を SoM から撤去する（ADR-039 の決定5）

第三者からの信頼は「AI 回答に出やすくなる**原因**」で、権威・エンティティ認知の軸（0〜30点）が担当する。SoM は「AI 回答での見え方という**結果**」を測る指標なので、原因側の重みを掛けない。`SomRawMetrics` から当該フィールドを落とした。

### 3. 飽和定数の再調整は別 PR にする

本 PR は「入力と単位の是正」に閉じる。`MENTION_DENSITY_SATURATION` / `MENTION_COUNT_SATURATION` / `PWIM_ALPHA` / `PWIM_BETA` の値は、本 PR 適用後の実データを見てから決め、オーナーの確認を取る（#60 の PR 2）。

## 理由

### 入力を Java にする理由

同じ回答文を渡しても LLM の申告値は揺れる。スコアの再現性（`.cursorrules` 4節の数理的再現性）を担保するには、確定的な量は決定論的に数えるしかない。

### 天井を戻すことを別の調整と分けて考える理由

天井が 0.12 から 0.60 へ戻ると、スコアは**全体に上がる**。この変化は「壊れていた式を直した結果」であり、飽和定数の妥当性とは別の話。混ぜると、値が動いた理由を後から説明できなくなる。

## 結果

- SoM の値が変わる。**上がる方向**（言及が正しく効くようになるため）。
- `GeminiVerificationAdapter` と `GeminiResultProcessor` が形態素解析サービスを直接使わなくなったため、依存から外した（`.cursorrules` 10節・孤立コード禁止）。
- 飽和定数が「回数」として効くことをテストで固定した（6回 < 12回 = 24回）。
- 言及が満点なら SoM 60、順位も満点なら 100 になることをテストで固定した。

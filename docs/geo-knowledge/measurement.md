---
last_verified: 2026-10-10
review_by: 2027-04-10
---

# AI の回答の揺れと、測り方

## まとめ

- AI の回答は毎回大きく揺れる。1回だけの「出た・出ない」には意味がほとんどない
- 「AI での順位」は測る意味がない。測るのは、たくさんの試行の中で出た割合
- 目安：1つの質問につき1日7〜8回の試行を、2〜4週間ためて集計する。同じ質問を5回より多く繰り返すより、言い換え・言語・AI の種類に散らすほうが効く
- 結果は「標本からの推定」として、信頼区間を付けて出す

## 揺れの大きさ

- 同じ質問を繰り返したとき、推薦されるブランドの一覧が同じになる確率は1%未満、順番まで同じなのは0.1%未満（ChatGPT・Claude・Google の AI、12の質問をそれぞれ60〜100回、計2,961回、2025年11〜12月）。「AI での順位」を出すツールは無意味で、出現率で見るべき。必要な試行回数は「未解決」とされている
  - 根拠: [実測★] SparkToro・Gumshoe（Gumshoe は計測ツール業者。Search Engine Journal の紹介） 2026-01-30 https://www.searchenginejournal.com/ai-recommendations-change-with-nearly-every-query-sparktoro/
  - 確認: 2026-10-10
- 引用元は1日で約65%入れ替わる。ブランドの出現は、引用元より安定している（ChatGPT・Gemini・AI モード・Perplexity、4分野×8質問、2026年1〜3月）
  - 根拠: [査読前] Schulte・Bleeker・Kaufmann「Don't Measure Once: Measuring Visibility in AI Search (GEO)」 2026-04-08 https://arxiv.org/abs/2604.07585 （著者の所属は未確認）
  - 確認: 2026-10-10
- 揺れの内訳は、同じ質問の繰り返し34.8%、質問の言語26.5%。ブランドそのものの違いで説明できるのは1.5%だけ。1回の回答でブランドを見分けられる信頼性は0.01ほど
  - 根拠: [査読前] Żatuchin「Where Does the Noise Come From? A Variance-Components Decomposition of Non-Determinism in LLM Brand Answers」 2026-07-14 https://arxiv.org/abs/2607.13304
  - 確認: 2026-10-10
- 引用の分布はべき分布で揺れが大きく、ドメイン間の差に見えるものの多くは測定の揺れの範囲に収まっていた。引用の指標は、回答の分布からの推定として信頼区間を付けて出すべき（Perplexity・OpenAI・Gemini、9日間の毎日と10分おきの取得）
  - 根拠: [査読前] Sielinski「Quantifying Uncertainty in AI Visibility」 2026-03-09（2026-08-26 改訂） https://arxiv.org/abs/2603.08924
  - 確認: 2026-10-10
- AI による概要は、同じ質問の2回の実行や小さな言い換えでぶれやすい
  - 根拠: [査読] Grossman ら「How Generative AI Disrupts Search」 SIGIR 2026 https://arxiv.org/abs/2604.27790
  - 確認: 2026-10-10
- 地域の検索では、同じ質問の繰り返しで結果の重なりが20〜33%。同じ市の中で場所を変えると、ChatGPT で36%、Google の AI で47%しか重ならない
  - 根拠: [実測★] BrightLocal 2026-09-16 https://www.brightlocal.com/research/local-ai-visibility-study/
  - 確認: 2026-10-10

## 必要な試行回数

- ブランドの出現を見るなら1つの質問につき1日7回以上、引用元まで見るなら8回以上。2〜4週間の移動集計を勧める（10日で標準誤差が0.10を下回り、21〜28日で0.033〜0.053）
  - 根拠: [査読前] Schulte ら 2026-04-08 https://arxiv.org/abs/2604.07585
  - 確認: 2026-10-10
- 同じ質問を5回より多く繰り返しても、信頼性はほとんど上がらない（6回目以降は1回あたり0.0003）。言語やモデルに散らすほうが上がる
  - 根拠: [査読前] Żatuchin 2026-07-14 https://arxiv.org/abs/2607.13304
  - 確認: 2026-10-10
- 測る手順の推奨：繰り返しの測定、言い換え、比べる相手（対照）、人による確認、複数の企業が同時に手を打つ影響への配慮
  - 根拠: [査読前] Martinez 2026-07-15 https://arxiv.org/abs/2607.14035
  - 確認: 2026-10-10

## 公式の計測手段

- Bing Webmaster Tools の「AI Performance」：Copilot・Bing の AI の要約・一部の提携先で、引用された回数、引用されたページ、根拠付けに使われた検索語が見られる（2026年2月に公開プレビュー）
  - 根拠: [公式] Microsoft Bing「Introducing AI Performance in Bing Webmaster Tools」 2026-02-10 https://blogs.bing.com/webmaster/February-2026/Introducing-AI-Performance-in-Bing-Webmaster-Tools-Public-Preview
  - 確認: 2026-10-10
- Google Search Console の生成 AI のレポート（AI による概要・AI モードでの表示回数など）。2026年6月に発表され、段階的に提供されている（提供範囲は報道による。公式の本文は 2026-10-10 に取得できなかった）
  - 根拠: [公式] Google「Introducing Search Generative AI performance reports in Search Console」 2026-06 https://developers.google.com/search/blog/2026/06/gen-ai-performance-reports
  - 確認: 2026-10-10

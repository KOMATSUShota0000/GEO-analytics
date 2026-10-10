---
last_verified: 2026-10-10
review_by: 2027-04-10
---

# 手に入れたページのどこが、答えの根拠に選ばれるか

## まとめ

- 本文の書き方（統計・引用・出典・冒頭の答え・見出しや表）は、実験や因果を調べた研究で効果がある。ただし効くのは「検索の候補に入った後」に引用の取り分を増やす範囲まで。候補に入るかどうかは変えない
- 実環境で、長く、複数のサービスにわたって効くと示された書き方は、まだない
- 公式の見解が割れている。Microsoft は「細かく区切る・FAQ・表・構造化データ」を勧め、Google は「AI 向けの特別な書き方も構造化データも不要」と書いている
- 構造化データ（JSON-LD）は、AI の引用を増やしたという実測がない

## 切り出しの単位

- Microsoft：AI は内容を小さな塊に分け、塊ごとに順位を付けて答えに組み立てる。勧めるのは、1〜2文の簡潔な答え、内容が分かる見出し（H2/H3）、箇条書き・番号付きの手順・比較表、FAQ（質問と答えの組はそのまま回答に使われうる）、それだけで意味が通る文、測れる事実
  - 根拠: [公式] Microsoft「Optimizing Your Content for Inclusion in AI Search Answers」 2025-10-08 https://about.ads.microsoft.com/en/blog/post/october-2025/optimizing-your-content-for-inclusion-in-ai-search-answers
  - 根拠: [公式] Microsoft Bing「Introducing AI Performance in Bing Webmaster Tools」 2026-02-10 https://blogs.bing.com/webmaster/February-2026/Introducing-AI-Performance-in-Bing-Webmaster-Tools-Public-Preview （見出し・表・FAQ、例やデータや出典による裏付け、更新、IndexNow を勧める）
  - 確認: 2026-10-10
- Google：「AI のために内容を細切れにする必要はない」「AI 向けに書き方を変える必要はない（同義語や大意は理解できる）」。いちばん効くのは、独自の視点がありありふれていない、人のための内容
  - 根拠: [公式] Google「Optimizing your website for generative AI features on Google Search」 2026-07-10 更新 https://developers.google.com/search/docs/fundamentals/ai-optimization-guide
  - 確認: 2026-10-10
- Gemini の根拠付けは、1つの質問につき約2,000語の枠を上位の情報源で分ける。1ページから選ばれるのは中央値377語で、書き換えずに原文のまま抜き出す。長いページほど使われる割合が下がる（1,000語未満で61%、3,000語超で13%）
  - 根拠: [実測★] DEJAN「How big are Google's grounding chunks?」 2025-12-20 https://dejan.ai/blog/how-big-are-googles-grounding-chunks/ （7,060質問。Gemini API の検索による根拠付けで観察）
  - 確認: 2026-10-10
- ChatGPT の引用の44%は、ページの最初の30%から取られている
  - 根拠: [実測★] Kevin Indig「The science of how AI pays attention」（データは計測業者 Gauge の提供） 2026-02-17 https://www.searchenginejournal.com/the-science-of-how-ai-pays-attention/567597/
  - 確認: 2026-10-10
- Microsoft は根拠付けについて、事実ごとの出どころがはっきりしていること、新しいこと、情報源どうしの矛盾を検出することを重視すると書いている
  - 根拠: [公式] Microsoft Bing「Evolving role of the index: From ranking pages to supporting answers」 2026-05-06 https://blogs.bing.com/search/May-2026/Evolving-role-of-the-index-From-ranking-pages-to-supporting-answers
  - 確認: 2026-10-10
- 両社に当てはまる無難な指針は、段落ごとに主語と対象を省かずに書くこと（推論。Microsoft の「それだけで意味が通る文」と、Gemini の原文抜き出しから。日本語は主語を省きやすい）

## 要素ごとの効果

| 要素 | 強さ | 一言 |
|---|---|---|
| 見出し・箇条書き・表 | 中 | Microsoft は推奨。因果を調べた研究では、引用される回数は増えるが、引用されるかどうかは変わらない |
| FAQ 形式 | 弱〜中 | Microsoft は推奨、Google は不要、業者の調査は逆の結果 |
| 結論を先に書く | 中 | Microsoft は推奨。引用は冒頭に集まる（観察のみ） |
| 数字・統計・引用・出典 | 中 | 実験では効く。実環境での再現は弱い |
| 更新日・新しさ | 中 | LLM は新しい日付の文章を上位にする。AI が引用するページは新しい |

- 見出し・箇条書き・表：同じ事実を述べた文書の組で、構造化した書き方と文章の書き方を入れ替えた。構造化すると、その文書の引用は1回答あたり +0.50（95%信頼区間 +0.20〜+0.84）増えたが、回答全体の引用数は増えず、引用されるかどうか（+4.5ポイント）ははっきりしなかった。つまり、すでに候補に入った文書の間で取り分を移す効果
  - 根拠: [査読前] Selvam・Ghosh「CITECHOICE」 2026-09-14 https://arxiv.org/abs/2609.15164
  - 確認: 2026-10-10
- FAQ：FAQ の構造化データがあるページのほうが、ChatGPT からの引用が少なかった（3.6回 対 4.2回、相関）
  - 根拠: [実測★] SE Ranking「How to optimize for ChatGPT」 2025-11 https://seranking.com/blog/how-to-optimize-for-chatgpt/
  - 確認: 2026-10-10
- 数字・統計・引用・出典：模擬環境（GPT-3.5 に Google の上位5件を渡す）で、引用文の追加で +41%、統計の追加で +30%、出典の明記で +28%。キーワードの詰め込みは逆効果。検索順位の低いサイトほど効果が大きく、5位のサイトで出典の明記が +115%。Perplexity でも同じ傾向。効く手法は分野で違う（引用文は歴史・人物、統計は法律・意見）
  - 根拠: [査読] Aggarwal ら「GEO: Generative Engine Optimization」 KDD 2024（初版 2023-11） https://arxiv.org/abs/2311.09735
  - 確認: 2026-10-10
- 上と食い違う研究：約1,900の質問・6分野で調べると、多くの手法は効かないか逆効果だった。54通りの組み合わせのうち有意に改善したのは3つで、質問応答では0。文脈の中での順位（従来の SEO）のほうがはるかに効く。皆が同じ手法を使うと効果は減る
  - 根拠: [査読] Puerto ら「C-SEO Bench: Does Conversational SEO Work?」 NeurIPS 2025 Datasets & Benchmarks（初版 2025-06） https://arxiv.org/abs/2506.11097
  - 確認: 2026-10-10
- 新しさ：7つの LLM（GPT-4o・LLaMA-3・Qwen-2.5 など）すべてが、日付の新しい文章を上位にした。上位10件の平均の出版年が最大4.78年新しくなり、関連度が同じ2文の好みが平均で最大25%入れ替わった。大きなモデルほど弱まるが、なくならない
  - 根拠: [査読前] 早稲田大学の研究者「Do Large Language Models Favor Recent Content?」 2025-09 https://arxiv.org/abs/2509.11353
  - 確認: 2026-10-10
- 新しさ：AI が引用する URL は、通常の検索結果より平均25.7%新しい（1,064日 対 1,432日）。ChatGPT が最も新しいものを引用し、AI による概要は古いものも引用する
  - 根拠: [実測★] Ahrefs「AI Assistants Prefer to Cite Fresher Content」 2025-07-28 https://ahrefs.com/blog/do-ai-assistants-prefer-to-cite-fresh-content （約1,700万件の引用）
  - 確認: 2026-10-10

## 構造化データ（Schema.org、JSON-LD）

- 効く側：Microsoft「構造化データは、商品・レビュー・FAQ・催しなどの印を付け、機械が確信を持って解釈できるようにする」
  - 根拠: [公式] Microsoft「Optimizing Your Content for Inclusion in AI Search Answers」 2025-10-08 https://about.ads.microsoft.com/en/blog/post/october-2025/optimizing-your-content-for-inclusion-in-ai-search-answers
  - 確認: 2026-10-10
- 効く側：Google の Martin Splitt「より多くの情報と確信を与えるが、順位を押し上げるものではない」
  - 根拠: [報道] Search Engine Journal 2025-05-02 https://www.searchenginejournal.com/server-side-vs-client-side-rendering-what-google-recommends/545946/
  - 確認: 2026-10-10
- 効かない側：Google「生成 AI の検索に構造化データは必須ではなく、追加すべき特別な schema.org の記述もない」。リッチリザルトのためには引き続き使ってよい。構造化データは見えている本文と一致させる
  - 根拠: [公式] Google「Optimizing your website for generative AI features on Google Search」 2026-07-10 更新 https://developers.google.com/search/docs/fundamentals/ai-optimization-guide
  - 根拠: [公式] Google「AI features and your website」 2025-12-10 更新 https://developers.google.com/search/docs/appearance/ai-features
  - 確認: 2026-10-10
- 効かない側：JSON-LD を追加した1,885ページを、追加しなかった約4,000ページと比べた（2025年8月〜2026年3月）。AI による概要 −4.6%、AI モード +2.4%、ChatGPT +2.2% で、いずれも有意差なし。対象は、もともと多く引用されていたページに限られる
  - 根拠: [実測★] Ahrefs「We Tracked 1,885 Pages Adding Schema」 2026-05-11 https://ahrefs.com/blog/schema-ai-citations/
  - 確認: 2026-10-10
- 効かない側：JSON-LD の中にだけ書いた架空の住所を、ChatGPT と Perplexity が読み取った。正しくない形式でも読んだので、構造化データとしてではなく、ただの文字として読んでいる
  - 根拠: [個人] Mark Williams-Cook の実験（Search Engine Roundtable の紹介） 2026-02-06 https://seroundtable.com/chatgpt-perplexity-structured-data-text-40862.html
  - 確認: 2026-10-10
- 判定：AI の引用を増やすという実測はない。Copilot については公式に「理解の助け」とされる。JSON-LD の中身も文字としては読まれるので、本文と矛盾しないことが大事

## 学術研究の流れ

- 生成エンジンの好みを、可視性に差がある文書の組から規則として取り出し、それに沿って書き換えると効果があった
  - 根拠: [査読] Wu ら「What Generative Search Engines Like and How to Optimize Web Content Cooperatively」（AutoGEO） ICLR 2026（初版 2025-10） https://arxiv.org/abs/2510.11438
  - 確認: 2026-10-10
- 2023〜2026年の GEO 研究の批判的レビュー：Aggarwal らの成果は「すでに文脈に入っている情報源」についての話で、自然に見つけてもらえることや流入は示していない。「安定して、長期に、複数のサービスにわたって因果効果を示した手法は一つもない」。再現性が高いのは、話題の関連性と文脈の中での位置。実際のサービスでは、情報源の重なりが小さく、実行ごとの揺れが大きい
  - 根拠: [査読前] Martinez「Optimizing Visibility in Generative Engines: A Critical Survey of Generative Engine Optimization (2023–2026)」 2026-07-15 https://arxiv.org/abs/2607.14035
  - 確認: 2026-10-10
- 実際の質問1.15万件で、AI による概要は51.5%に表示された。Google 検索・AI による概要・Gemini の情報源の重なりは小さい（Jaccard 係数で平均0.2未満）。AI による概要は Google 自身のコンテンツを引きやすく、同じ質問の2回の実行や小さな言い換えでぶれやすい
  - 根拠: [査読] Grossman ら「How Generative AI Disrupts Search」 SIGIR 2026 https://arxiv.org/abs/2604.27790
  - 確認: 2026-10-10
- Google 検索と、Google・OpenAI・Perplexity の5つの生成検索を比べた。生成検索は情報源の取り方がまったく違い、時間や実行のたびに出力が変わる
  - 根拠: [査読] Kirsten ら「Characterizing Web Search in The Age of Generative AI」 Findings of ACL 2026 https://aclanthology.org/2026.findings-acl.526/
  - 確認: 2026-10-10

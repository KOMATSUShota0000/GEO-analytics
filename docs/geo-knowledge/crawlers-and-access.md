---
last_verified: 2026-10-10
review_by: 2027-01-10
---

# AI はどうやってページを手に入れているか

変化が速い分野。各社の方針・クローラーの挙動は3か月で確かめ直す。

## まとめ

- 検索用のクローラーを拒否すると、その AI の検索の回答から外れると OpenAI・Perplexity が公式に書いている。llms.txt には同じような効果の公式表明がない
- OpenAI・Anthropic・Perplexity の主なクローラーは JavaScript を実行しない（一次の測定は2024年12月の1件のみ）。Google（Gemini を含む）と Bing は実行する
- robots.txt は「お願い」で、守られないことが多い。確実に止めるのはネットワークの段階の遮断（Cloudflare など）
- llms.txt は、AI 検索の回答に使われている証拠がない

## サービスごとの検索索引

- ChatGPT は、OpenAI 自前の索引（社内名 Labrador）と、外部業者経由で取った Google の検索結果を使う。公式文書は検索の提供元を「第三者の検索提供元」としか書いていない
  - 根拠: [実測★] Peec AI「ChatGPT built its own search index」 2026-10-07 https://peec.ai/blog/chatgpt-built-its-own-search-index
  - 根拠: [実測★] Falia（Resoneo の調査と米国の裁判での証言を紹介） 2026-09-08 https://falia.co/en/insights/ai-visibility/what-search-engine-does-chatgpt-use/
  - 根拠: [報道] Search Engine Land（The Information の報道の紹介：OpenAI が SerpApi 経由で Google の結果を使う） 2025-08 https://searchengineland.com/openai-chatgpt-serpapi-google-search-results-461226
  - 確認: 2026-10-10
- 2026年5〜7月に ChatGPT の応答データに出ていた情報源の欄の解析では、推論モードの有料アカウントで、外部業者経由の Google 結果が75.3%。Bing はほぼ見えなかった
  - 根拠: [実測★] Falia（Resoneo の調査、Search Engine Land 2026-08-17 の紹介） 2026-09-08 https://falia.co/en/insights/ai-visibility/what-search-engine-does-chatgpt-use/
  - 確認: 2026-10-10
- Google の AI による概要・AI モードは Google 検索の索引を使う。質問を複数の関連検索に展開して材料を集める（query fan-out）。根拠のリンクに出る条件は「索引に入っていて、スニペットを表示できること」
  - 根拠: [公式] Google「AI features and your website」 2025-12-10 更新 https://developers.google.com/search/docs/appearance/ai-features
  - 根拠: [公式] Google「Optimizing your website for generative AI features on Google Search」 2026-07-10 更新 https://developers.google.com/search/docs/fundamentals/ai-optimization-guide
  - 確認: 2026-10-10
- Perplexity は自前の索引（数千億ページ）を持ち、文書を細かく分けた単位で順位を付けた抜粋を返す
  - 根拠: [公式] Perplexity「Introducing the Perplexity Search API」 2025-09-25 https://hub-prod.perplexity.ai/hub/blog/introducing-the-perplexity-search-api
  - 確認: 2026-10-10
- Claude の Web 検索は Brave Search を使っていると見られる（2025年3月に Anthropic の委託先一覧へ追加）。Anthropic は検索の提供元を公式には明言していない。2026年の状況は二次情報しかない
  - 根拠: [報道] TechCrunch 2025-03-21 https://techcrunch.com/2025/03/21/anthropic-appears-to-be-using-brave-to-power-web-searches-for-its-claude-chatbot/
  - 確認: 2026-10-10
- Microsoft Copilot は Bing の索引を使う。Microsoft は「主要な AI アシスタントのほぼすべてを当社の根拠付けが支えている」と書いている（製品名は挙げていない）
  - 根拠: [公式] Microsoft Bing「Elevating the Role of Grounding on the AI Web」 2026-02-12 https://blogs.bing.com/search/February-2026/Elevating-the-Role-of-Grounding-on-the-AI-Web
  - 確認: 2026-10-10

## クローラーの種類と、robots.txt で止めたときに起きること

学習用・検索用・利用者の依頼で都度取りに来るもの、の3種類がある。止めて困るのは検索用と都度取得。学習用を止めても検索の回答には直接は響かない（各社の公式の説明）。

| 会社 | 学習用 | 検索用 | 都度取得 |
|---|---|---|---|
| OpenAI | GPTBot | OAI-SearchBot | ChatGPT-User |
| Google | Google-Extended（robots.txt 上の名前のみ） | Googlebot | 公式一覧に AI 専用のものなし |
| Perplexity | 公開なし | PerplexityBot | Perplexity-User |
| Anthropic | ClaudeBot | Claude-SearchBot | Claude-User |
| Microsoft | 専用の名前なし | Bingbot | 固有の名前なし |

- OpenAI：GPTBot を拒否すると学習に使われなくなる。OAI-SearchBot を拒否したサイトは「ChatGPT の検索の回答に表示されない」。ChatGPT-User は利用者の操作による取得なので「robots.txt が適用されない場合がある」
  - 根拠: [公式] OpenAI「Overview of OpenAI Crawlers」 日付表示なし（2026-10-10 閲覧） https://developers.openai.com/api/docs/bots
  - 確認: 2026-10-10
- Google：Google-Extended は、Gemini の学習と、Gemini アプリ・Vertex AI での根拠付けへの利用を制御する。Google 検索（AI による概要・AI モードを含む）への掲載と順位には影響しない
  - 根拠: [公式] Google「Google's common crawlers」 2026-07-14 更新 https://developers.google.com/search/docs/crawling-indexing/google-common-crawlers
  - 確認: 2026-10-10
- 上と食い違う研究：Google-Extended を拒否しているサイトは、Gemini だけでなく AI による概要でも有意に取り上げられにくかった。2025年12月の米国の検索、拒否していたのは大手21サイト（NYT・CNN・Yelp など）。サイトの規模・種類は補正済みだが観察研究で、因果は言えない
  - 根拠: [査読] Grossman ら「How Generative AI Disrupts Search」 SIGIR 2026（2026-04-30 公開） https://arxiv.org/abs/2604.27790
  - 確認: 2026-10-10
- Google の AI による概要から外すには nosnippet などを使うが、通常の検索のスニペットも同時に失う
  - 根拠: [公式] Google「AI features and your website」 2025-12-10 更新 https://developers.google.com/search/docs/appearance/ai-features
  - 確認: 2026-10-10
- 英国 CMA は2026年6月、「AI 機能だけを拒否でき、通常の検索順位は下げない手段」を Google に命じた。日本で提供されるかは不明
  - 根拠: [報道] Irish News（PA 配信） 2026-06-03 https://www.irishnews.com/news/uk/google-must-allow-publishers-to-opt-out-of-ai-search-results-under-new-cma-rules-MJIER3TSCNIOXHSJJJWGWTLGJQ/
  - 確認: 2026-10-10
- Perplexity：PerplexityBot を拒否すると Perplexity の検索結果に出ない。Perplexity-User は利用者の依頼による取得なので robots.txt を「原則無視」する
  - 根拠: [公式] Perplexity「Perplexity Crawlers」 日付表示なし（2026-10-10 閲覧） https://docs.perplexity.ai/guides/bots
  - 確認: 2026-10-10
- Anthropic：ClaudeBot を拒否すると今後の学習から外れる。Claude-SearchBot を拒否すると検索結果での「見え方と正確さが下がる可能性」、Claude-User を拒否すると利用者の指示による検索での見え方が下がる可能性
  - 根拠: [公式] Anthropic「Does Anthropic crawl data from the web, and how can site owners block the crawler?」 2026-04-07 https://support.claude.com/en/articles/8896518-does-anthropic-crawl-data-from-the-web-and-how-can-site-owners-block-the-crawler
  - 確認: 2026-10-10
- Microsoft：Bingbot を止めると Bing と Copilot の両方から外れる。NOARCHIVE は Copilot の回答に使わせない指定、NOCACHE は URL・題名・スニペットだけを使わせる指定。どの文を表示・引用してよいかを指定する data-snippet 属性もある
  - 根拠: [報道] Search Engine Journal（Bing Webmaster Guidelines の改訂の紹介） 2026-02-27 https://www.searchenginejournal.com/bing-adds-geo-to-official-guidelines-expands-ai-abuse-definitions/568442 （公式本文: https://www.bing.com/webmasters/help/webmaster-guidelines-30fba23a 。2026-10-10 は本文を取得できなかった）
  - 確認: 2026-10-10

## robots.txt は守られるか

- AI 検索のクローラーは robots.txt をほとんど確認しない。厳しい Disallow を守る率は30.7%、クロールの間隔のような緩い指示は60.9%
  - 根拠: [査読前] Kim ら「Scrapers selectively respect robots.txt directives」 2025-05（2025-10-23 改訂） https://arxiv.org/abs/2505.21733
  - 確認: 2026-10-10
- robots.txt などで守るのは限界があり、ネットワークの段階で遮断する仕組み（リバースプロキシ）が有望。ただし使われていない
  - 根拠: [査読] Liu ら「Somesite I Used To Crawl」 IMC 2025 https://arxiv.org/abs/2411.15091
  - 確認: 2026-10-10

## JavaScript を実行するか

| クローラー | 実行するか |
|---|---|
| GPTBot・OAI-SearchBot・ClaudeBot・PerplexityBot | しない（JS ファイルは取得するが実行しない） |
| Googlebot、Gemini が使う AI クローラー | する |
| Bingbot | する（ただし大規模では限界がある） |
| AppleBot | する |
| ChatGPT-User・Claude-SearchBot・Claude-User・Perplexity-User | 不明 |

- GPTBot・OAI-SearchBot・ClaudeBot・PerplexityBot は JavaScript を実行しない。AppleBot は実行する。Microsoft Copilot は固有のユーザーエージェントがないので測れていない
  - 根拠: [実測] Vercel・MERJ「The rise of the AI crawler」 2024-12-17 https://vercel.com/blog/the-rise-of-the-ai-crawler （ホスティング業者。GEO のツールは売っていない）
  - 確認: 2026-10-10
- 2026年のブログは軒並み「実行しない」と書くが、確認できた一次の測定は上の2024年12月の1件だけ。都度取得のクローラーについての公式表明・一次測定は見つからない
  - 確認: 2026-10-10
- Gemini が使う Google の AI クローラーは、Googlebot と同じ描画の仕組み（Web Rendering Service）で JavaScript を実行する。内容中心のサイトで JavaScript を必須にするのは欠点で、サーバー側の描画か静的な HTML を勧める
  - 根拠: [報道] Search Engine Journal（Google の Martin Splitt へのインタビューの紹介。元は Faber Company の Kenichi Suzuki 氏による） 2025-05-02 https://www.searchenginejournal.com/server-side-vs-client-side-rendering-what-google-recommends/545946/
  - 確認: 2026-10-10
- Bingbot は Edge を使って描画し、常に最新版に保たれる。Bing は大規模では限界があるとして、サーバー側での描画を勧めてきた。古い情報
  - 根拠: [報道] Search Engine Journal 2019年ごろ（日付未確認） https://searchenginejournal.com/bings-web-crawler-goes-evergreen-improves-javascript-crawling/329667
  - 確認: 2026-10-10
- Microsoft は、AI はタブや開閉メニューに隠れた内容を描画しないことがある、と書いている。重要な情報を PDF や画像の中だけに置かないよう勧めている
  - 根拠: [公式] Microsoft「Optimizing Your Content for Inclusion in AI Search Answers」 2025-10-08 https://about.ads.microsoft.com/en/blog/post/october-2025/optimizing-your-content-for-inclusion-in-ai-search-answers
  - 確認: 2026-10-10
- ChatGPT は Google の検索結果も使うので、JavaScript で描かれた内容が Google 経由で入る道はありうる（推論。未検証）

## ボット対策（Cloudflare）

- 2025-07-01：新しく登録したドメインで、AI クローラーを既定で遮断するようにした
  - 根拠: [公式] Cloudflare のプレスリリース 2025-07-01 https://www.cloudflare.com/press/press-releases/2025/cloudflare-just-changed-how-ai-crawlers-scrape-the-internet-at-large/
  - 確認: 2026-10-10
- 2025-08：Perplexity が遮断を避けるため、Chrome を装った未申告のクローラーを使っていると Cloudflare が指摘した（Cloudflare 側の主張。Perplexity 側の見解は未確認）
  - 根拠: [公式] Cloudflare「Perplexity is using stealth, undeclared crawlers to evade website no-crawl directives」 2025-08 https://blog.cloudflare.com/perplexity-is-using-stealth-undeclared-crawlers-to-evade-website-no-crawl-directives/
  - 確認: 2026-10-10
- 2026-07-01：AI の通信を「検索」「エージェント（利用者の代わりに都度取りに来るもの）」「学習」の3種類に分けて制御できるようにした
  - 根拠: [公式] Cloudflare「New options to manage AI traffic」 2026-07-01 https://developers.cloudflare.com/changelog/post/2026-07-01-ai-traffic-options/
  - 確認: 2026-10-10
- 2026-09-15 以降：新しいドメインでは、広告を表示するページで「学習」と「エージェント」を既定で遮断し、「検索」は許可する。学習と検索を兼ねるクローラー（Googlebot など）は、「AI 学習を拒否」の設定なら検索用には通る
  - 根拠: [公式] Cloudflare「Block AI bots」 2026-07-01 更新 https://developers.cloudflare.com/bots/additional-configurations/block-ai-bots/
  - 根拠: [公式] Cloudflare「Have it both ways: stay discoverable in search while disallowing AI training」 2026-09-15 https://blog.cloudflare.com/accountable-mixed-use-ai-crawlers/
  - 確認: 2026-10-10
- 学習を止める設定を使うサイトは17%、検索用ボットを止めているサイトは1%未満
  - 根拠: [公式] Cloudflare「Have it both ways」 2026-09-15 https://blog.cloudflare.com/accountable-mixed-use-ai-crawlers/
  - 確認: 2026-10-10
- ネットワークの段階の遮断は robots.txt より先に効く。Cloudflare は名乗り（ユーザーエージェント）だけでなく IP などで本物のクローラーかを確かめるので、名乗りを真似ても同じ扱いにはならない（推論。Cloudflare の「確認済みのボット」の仕組みから）
- 都度取得が止まると引用がどれだけ減るかの実測は見つからない（不明）

## llms.txt

- Google は「Google 検索自体は使わない。特別なファイル・マークアップ・Markdown は不要」と書いている
  - 根拠: [公式] Google「Optimizing your website for generative AI features on Google Search」 2026-05-15 公開・2026-07-10 更新 https://developers.google.com/search/docs/fundamentals/ai-optimization-guide
  - 確認: 2026-10-10
- OpenAI・Anthropic・Perplexity・Microsoft が AI 検索で llms.txt を読むという公式表明は見つからない（不明）
  - 確認: 2026-10-10
- 13.7万ドメインのうち28%が llms.txt を置いているが、その97%は2026年5月に一度も取得されなかった。取得していた AI は主に GPTBot と開発用の Claude Code で、OAI-SearchBot や PerplexityBot はわずか
  - 根拠: [実測★] Ahrefs「We Analyzed 137K Sites: 97% of llms.txt Files Never Get Read」 2026-06-15 https://ahrefs.com/blog/llmstxt-study/
  - 確認: 2026-10-10
- 約30万ドメインで、llms.txt の有無と AI からの引用回数に関係はなかった。予測モデルから外すと精度が上がった
  - 根拠: [実測★] SE Ranking（Search Engine Journal の紹介） 2025-11-20 https://www.searchenginejournal.com/llms-txt-shows-no-clear-effect-on-ai-citations-based-on-300k-domains/561542/
  - 確認: 2026-10-10

## 仮説の判定（2026-10-10）

- H2「主要な AI クローラーの多くは JavaScript を実行しない。JavaScript なしでは本文が出ないサイトは、それらの AI から空に見える」：おおむね支持。ただし一次の測定は2024年12月の1件だけで、都度取得のクローラーは不明。空に見えるのはクローラーが取りに来たときの話
- H3「llms.txt の有無は、今は AI の回答にほとんど影響しない」：支持。例外は開発用の AI（Claude Code など）で、AI 検索とは別の用途
- H4「robots.txt やボット対策での締め出しのほうが、llms.txt よりはっきり影響する」：支持。ただし検索用ボットを止めているサイトは1%未満で、締め出しで引用がどれだけ減るかの実測はない。robots.txt 自体は守られないことも多く、決定的なのはネットワークの段階の遮断

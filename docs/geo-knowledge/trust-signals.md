---
last_verified: 2026-10-10
review_by: 2027-04-10
---

# AI はどんな情報源を「信頼」するか

信頼には2つの経路がある。(a) モデルが学習で覚えている知識と、(b) 検索で取ってきた情報源の中からの選択。

## まとめ

- どちらの経路でも、いちばん一貫して効くのは第三者のサイトでの言及の多さ（報道・Wikipedia・Q&A サイト・口コミ）。ただし相関で、因果は確かめられていない
- 引用元の内訳は調査で食い違う。一般的な比較の質問では第三者のメディアが、地域名やブランド名を含む質問では自社サイトと掲載情報（地図・店舗情報）が多い、と読むと整合する（推論）
- 情報の一貫性（社名・住所などが各所でそろっているか）が AI の回答に効くかを直接測った研究は見つからない

## (a) 学習で覚えている知識

- ChatGPT が Web 検索を使うのは質問の34.5%（2026年2月）で、2024年末の46%から減った。残りは学習済みの知識で答えている
  - 根拠: [実測★] Semrush「ChatGPT traffic analysis: Insights from 17 months of clickstream data」 2026（2024-10〜2026-02 の米国のクリック履歴） https://www.semrush.com/blog/chatgpt-search-insights/ （本文は未確認。検索結果の要約で確認）
  - 確認: 2026-10-10
- 業界名だけの質問（ブランド名を含まない質問）で名前が出る割合は、世界的なブランドで73%、中堅で44%、ニッチで11%
  - 根拠: [査読前・★] Kumar（計測業者 Ranqo）「Generative Engine Optimization at Scale」 2026-06-18 https://arxiv.org/abs/2606.20065
  - 確認: 2026-10-10
- AI 検索は大手ブランドに偏る
  - 根拠: [査読前] Chen・Wang・Chen・Koudas（トロント大学）「Generative Engine Optimization: How to Dominate AI Search」 2025-09-10 https://arxiv.org/abs/2509.08919
  - 確認: 2026-10-10
- 学習で覚えた知識を動かすのは、長い期間にわたる第三者での言及の量だと考えられる。学習データに入るまで時間がかかるので、すぐには動かない（推論。直接測った研究は少ない）
- GPTBot・ClaudeBot を拒否すると今後の学習から外れる（`crawlers-and-access.md` を参照）

## (b) 検索で取ってきた情報源の中からの選択

- Google の AI 機能は、通常の検索の順位付けと品質の仕組みに根ざしている。作られた「言及」を集めても、思うほど役に立たない
  - 根拠: [公式] Google「Optimizing your website for generative AI features on Google Search」 2026-07-10 更新 https://developers.google.com/search/docs/fundamentals/ai-optimization-guide
  - 確認: 2026-10-10
- AI による概要で引用されたページのうち、同じ質問で検索上位10位以内のものは37.9%（2026年3月の分析、86.3万質問）。2025年7月の76%から下がった。質問の展開が広がったためと見られる。AI による概要で最も多く引用されるドメインは YouTube
  - 根拠: [実測★] Ahrefs「Update: 38% of AI Overview Citations Pull From The Top 10」 2026 https://ahrefs.com/blog/ai-overview-citations-top-10/
  - 確認: 2026-10-10
- AI による概要でのブランドの見え方と最も強く相関したのは、Web 上でのブランドへの言及（スピアマンの相関 0.664）。ブランド名を含むリンクの文言 0.527、ブランド名の検索数 0.392、ドメイン評価 0.326、被リンクは 0.218（7.5万ブランド。相関で、因果ではない）
  - 根拠: [実測★] Ahrefs「An Analysis of AI Overview Brand Visibility Factors (75K Brands Studied)」 2025-05-26 https://ahrefs.com/blog/ai-overview-brand-correlation/
  - 確認: 2026-10-10
- AI 検索は、企業自身のサイトや SNS より、第三者のメディア（earned media）を圧倒的に好む。Google の通常の検索とは対照的。サービスごと・言語ごとに差がある
  - 根拠: [査読前] Chen ら 2025-09-10 https://arxiv.org/abs/2509.08919
  - 確認: 2026-10-10
- ChatGPT の引用と最も強く相関したのは、被リンク元のドメイン数とドメインの信頼度。Reddit・Quora・口コミサイトでの存在、3か月以内の更新、表示の速さも相関した
  - 根拠: [実測★] SE Ranking「How to optimize for ChatGPT」 2025-11 https://seranking.com/blog/how-to-optimize-for-chatgpt/ （12.9万ドメイン。相関）
  - 確認: 2026-10-10

### 引用元の内訳（調査で食い違う）

- 第三者のメディアが84%、報道だけで27%、広告記事は0.3%。引用の半分以上が12か月以内に公開されたもの（ChatGPT・Claude・Gemini、2,500万リンク）。「第三者」には他社の企業サイトも含めている
  - 根拠: [実測★] Muck Rack（PR ツール業者）「What Is AI Reading?」第3版 2026-05 https://aimagazine.com/globenewswire/3290268
  - 確認: 2026-10-10
- 企業自身が管理する情報源が86%（公式サイト44%、店舗情報などの掲載42%、口コミ・SNS 8%）。掲示板は2%。地域を含む質問が中心で、小売・金融・医療・飲食の4業界（ChatGPT・Gemini・Perplexity、680万引用）
  - 根拠: [実測★] Yext（店舗情報の管理業者） 2025-10-09 https://www.yext.com/blog/ai-citations-86-percent-of-sources-are-brand-managed
  - 確認: 2026-10-10
- 引用の約78%が企業のサイトに向かった。企業以外では YouTube、Reddit、編集メディア、Wikipedia の順。最も効く形式は「おすすめ○選」型の記事（引用の約21%）
  - 根拠: [査読前・★] Kumar（Ranqo） 2026-06-18 https://arxiv.org/abs/2606.20065
  - 確認: 2026-10-10

## 個々の要因

- 情報の一貫性（社名・住所・電話・サービス内容）：AI の回答への効果を直接測った研究は見つからない（不明）
  - 確認: 2026-10-10
- 出どころと矛盾：Microsoft は、根拠付けでは事実ごとの出どころがはっきりしていることを重視し、情報源どうしの矛盾を検出すると書いている。各所で情報がそろっていることが効くと考える間接的な根拠になる（推論）
  - 根拠: [公式] Microsoft Bing「Evolving role of the index」 2026-05-06 https://blogs.bing.com/search/May-2026/Evolving-role-of-the-index-From-ranking-pages-to-supporting-answers
  - 確認: 2026-10-10
- E-E-A-T：Google は AI 機能も通常の品質の仕組みに根ざすと書く（上記）。AI の回答で E-E-A-T を直接測った研究はない
  - 確認: 2026-10-10
- 新しさ：`citation-selection.md` の「新しさ」を参照

## AI ごとの引用元

- 世界：AI による概要では YouTube が最も多く引用される
  - 根拠: [実測★] Ahrefs 2026 https://ahrefs.com/blog/ai-overview-citations-top-10/
  - 確認: 2026-10-10
- 日本：`japan-market.md` の「日本語での引用元」を参照

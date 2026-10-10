---
last_verified: 2026-10-10
review_by: 評価項目・クロール・計測のコードを変えるとき
code_snapshot: a8e9715
---

# 評価項目・クロール・計測への当てはめ（2026-10-10 時点）

ほかのファイルの知見を、このプロダクトに当てはめた評価と提案。コードはコミット `a8e9715` の時点で読んだ。評価項目・クロール・計測のコードを変えたら、同じ PR でこのファイルを直すか、古くなった項目にその旨を書く。全体が古くなったら、削除してよいかオーナーに聞く。

ここに書いた提案は、どれもまだ決まっていない。直すときは別の issue にする。

## 今の作り（a8e9715）

| 何 | どこ |
|---|---|
| 評価項目と素点の満点 | `domain/enums/RubricCriterionId.java` |
| AI が本文から判定する10項目の指示 | `infrastructure/ai/RubricAuditPrompts.java` |
| 技術・地図情報の判定、llms.txt の確認 | `application/service/AiRubricAuditService.java` |
| 3つの軸（本文・技術・権威）への集約 | `domain/service/GeoVisibilityCalculatorService.java` |
| 第三者の言及の数え方 | `domain/service/ThirdPartyMentionScorer.java`、`application/service/ThirdPartyMentionMeasurementService.java` |
| 見出しの判定 | `application/dto/CrawledPageData.java` の `headingHierarchyOk` |
| ページの取得 | `infrastructure/crawler/JsoupPageExtractor.java` |
| AI による概要の取得 | `application/service/AsyncSgeMeasurementService.java` |

評価に効く事実（a8e9715 時点）：

- 最終点は、本文50点＋技術20点＋権威30点
- 技術は、JSON-LD・見出し・llms.txt の3つがそろって満点、2つで半分、1つ以下で0点。JSON-LD と見出しがそろったサイトでは、llms.txt の有無だけで総合点が10点動く
- 見出しの判定は「h1 と h2 が1つ以上ある」だけ
- 地図情報は口コミの件数だけで判定し、50件で満点。地域の業種でだけ権威の軸に入る
- 第三者の言及は、社名だけで Google を1回検索し（職種名を付けた同名対策はまだ入っていない）、上位の結果に出た自社以外のドメインの数を数えて、8つで満点
- ページは JavaScript を実行しない取得で、Chrome を名乗る。robots.txt は読まない
- AI による概要の取得は、1つの質問につき1回

## 評価項目ごとの判定

| 項目 | 根拠の強さ | 提案 | 理由 |
|---|---|---|---|
| DIRECT_ANSWER_FIRST | 中 | 残す | Microsoft が公式に推奨。引用は冒頭に集まる。Gemini は原文を抜き出す（`citation-selection.md`） |
| ATOMIC_FACTS | 中 | 残す | 統計の追加で +30%（模擬環境）。Microsoft も「測れる事実」を推奨。実環境での再現は弱い |
| SOLUTION_SCENARIOS | 弱い | 直す | 直接確かめた研究はない。Google の「ありふれていない内容」と質問の展開からの間接的な支持だけ。「どの相談・質問に答えているか」を見る形にし、計測用の質問と対応づける |
| VERIFIABLE_AUTHORITY | 弱い | 直す | 権威的な口調は模擬実験で効果が小さい。資格・登録番号・受賞に、確かめられる出典が付いているかを見る形にする |
| FAQ_PRESENCE | 弱〜中（食い違い） | 直す（統合） | Microsoft は推奨、Google は不要、業者の調査は逆の結果。NUMBERED_PROCESS_FLOW と合わせて「質問と答え・手順・比較を、見出し・箇条書き・表で区切っているか」の1項目にする |
| NUMBERED_PROCESS_FLOW | 弱い | 直す（統合） | Microsoft が番号付きの手順を推奨。構造化した書き方の因果効果は、候補に入った後に取り分を増やす範囲まで |
| ENTITY_BIOGRAPHY | 弱い | 直す | 会社や人物を特定できる事実（正式名称・所在地・設立年・代表者・実績）が本文にあるかに絞る |
| LOCAL_CONTEXT | 中（地域の業種だけ） | 直す | 地域の検索では近さと地域の文脈が効く（`japan-market.md`）。地域の業種だけで採点する |
| PRICE_AND_CONSTRAINTS | 根拠なし | 残す（仮説と明示） | 直接の研究がない。比較や検討の質問で使われうる（推論）。計測で確かめるまでは仮説として扱う |
| EXTERNAL_CITATIONS | 中 | 残す | 出典の明記で +28%、引用文の追加で +41%（模擬環境）。Microsoft も根拠のない主張を避けるよう求めている |
| MACHINE_READABILITY_SIGNAL | JSON-LD：弱い／見出し：中／llms.txt：根拠なし | 大きく直す | llms.txt は点数から外し、参考情報として表示する。JSON-LD は本文との一致を軽く確かめる程度にする。見出しは「内容が分かる H2/H3 があるか」に変える。空いた点を「届く・読み解ける」の検査に回す |
| MEO_TRUST_SCORE | 日本の AI モードでは中〜強。ChatGPT では不明 | 直す | 日本の AI モードの地域の回答の約7割が Google の店舗情報を参照する。件数だけでなく、プロフィールの充実度（カテゴリ・営業時間・説明・写真）、評価点、最近の口コミも見る |
| THIRD_PARTY_MENTIONS | 中〜強（相関は一貫、因果は未確認） | 残す（測り方を直す） | 下に書く |

### THIRD_PARTY_MENTIONS の測り方の問題

- 自社の SNS や求人サイトも「第三者」として数える
- 社名だけで検索するので、同名の別の会社も混ざる
- AI が実際に引用する種類のサイト（報道、Wikipedia、PR TIMES、note、知恵袋、口コミサイト、業界メディア）と区別していない（`trust-signals.md`、`japan-market.md`）
- 名の通った会社なら、すぐに満点になる可能性がある（推論。実データで要確認）

## 足りない項目

`geo-model.md` の段階ごとに挙げる。

| 段階 | 足りない検査 |
|---|---|
| 1 届く | AI のクローラーごとの robots.txt の判定（学習用を止めているのは問題なし、検索用と都度取得を止めているのは重大、と分けて出す）／ボット対策による遮断の兆候／noindex・nosnippet・data-nosnippet・NOARCHIVE・NOCACHE／HTTP の状態 |
| 2 読み解ける | JavaScript なしの HTML に本文がどれだけあるか（描画後との比）／本文がタブや開閉メニュー、画像、PDF に閉じ込められていないか |
| 3 候補に入る | Google と Bing の索引に入っているか。R-32 で順位の追跡は禁止。索引に入っているかの確認は順位の追跡に当たらないと考えるが、オーナーの判断が要る |
| 4 引用したくなる | 更新日（本文の表示と dateModified） |
| 5 外での裏付け | 第三者の言及の種類／Wikipedia・Wikidata に載っているか／社名・住所・電話・サービス内容が各所でそろっているか（根拠は弱いが、確かめるのは安い。仮説として扱う） |
| 計測 | AI による概要以外（日本の利用者が最も多いのは ChatGPT）／1つの質問につき複数回の試行と信頼区間（`measurement.md`） |

## 配点の釣り合い

- 本文50点は、根拠に比べて重い。本文の工夫の効果は「候補に入った後」に限られる
- 「届く・読み解ける」は足し算の点ではなく関門。満たさないときは総合点に上限をかけるか、点とは別に大きな警告を出すほうが根拠に合う
- 外での裏付けの30点は妥当で、上げてもよい
- 配点のたたき台（決めるのはオーナー）：本文35〜40点、届く・読み解ける（関門＋10〜15点）、外部35〜40点、llms.txt 0点

## クロール方式への提案

1. 二重に取得する。A は JavaScript を実行しない素の HTML（OpenAI・Anthropic・Perplexity からの見え方に近い）、B はヘッドレスブラウザで描画した後（Google・Gemini・Bing からの見え方に近い）。本文は A で採点し、A が空なら最優先の改善点として出す。B は A が薄いときだけ動かして、費用を抑える（R-23）
2. 今の「Chrome を名乗る取得」は、届くかどうかを甘く見積もる。ボット対策は名乗りで遮断することが多いので、AI のクローラーが弾かれるサイトでも、こちらは取れてしまう
3. robots.txt の判定は、確実で安いので最優先で入れる
4. AI のクローラーを名乗って取得してみる方法は、名乗りでの遮断は見つけられる。ただし Cloudflare などは IP で本物かを確かめるので、本物は通るのにこちらだけ弾かれる誤警報が起きる。結果は「可能性あり」と出し、クライアントにボット対策の設定を確かめてもらう流れにする。他社のクローラーを名乗ってよいかは、オーナーの判断が要る
5. 応答のヘッダーから Cloudflare の利用を検出し、2026-09-15 からの既定（広告を出すページでは都度取得を遮断）に当たるかを注意として出す
6. meta robots・X-Robots-Tag・data-nosnippet・NOARCHIVE を読み取る
7. 冒頭の判定は、見た目の順ではなく HTML の順で行う
8. 自社のクローラーが何と名乗り、robots.txt を守るかも見直しの対象。競合のサイトも取得するのに、今は robots.txt を読んでいない

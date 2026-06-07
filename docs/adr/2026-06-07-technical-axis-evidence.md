# ADR-038: 「AIが読みやすい構造」軸のサイト固有エビデンス露出（エビデンス Phase2）

## 日付
2026-06-07

## 状況
ADR-035 でコンテンツの充実度（Phase1）にサイト固有エビデンス（ルーブリック直接引用）を露出し、オーナーから「自分のサイトを読んでくれている実感が信頼を生む」と高評価。技術軸（AIが読みやすい構造）にも同様のサイト固有エビデンスを出したい。

調査の結果、技術評価の所見（`CrawledPageData.seoTechnicalEvidenceSummary` = 例「Schema.org: 未実装, Description: 適切, Heading: H1欠落, H2あり, Robots: メタなし」）は、**既に `jobs.self_crawled_page_json` に永続化済み**で、`JobAnalysisBenchmarkAssembler.attach` が `parseCrawl` でパース済みだった（機械可読性スコア算出に使用）。＝**新規DB移行・新規パース不要**で露出できる。

## 決定
技術評価の要約文をジョブ解析レスポンスに露出し、「AIが読みやすい構造」軸をクリックで展開してサイト固有の技術所見を表示する。

**BE（最小変更・既存パース再利用）**:
- `JobAnalysisBenchmarkAssembler.BenchmarkAttach` に `String technicalEvidence` を追加し、`attach()` で `crawl.seoTechnicalEvidenceSummary()` を充填（既に L39 で crawl をパース済み）。
- `JobAnalysisDetailResponse` に `technical_evidence`（String）を追加。`JobController` が `bench.technicalEvidence()` を渡す。

**FE**:
- `types/analysis.ts`: `JobAnalysisDetail.technicalEvidence`（string）をパース（snake/camel両対応・空は undefined）。
- `GeoScoreBreakdown`: 「AIが読みやすい構造」軸に「根拠を見る」トグル。`TechnicalEvidencePanel` が要約文を `, / 、` で項目分割して箇条書き表示（分割できなければ原文表示）。

## 理由
- 技術所見は各サイトの実クロール結果（Schema.org有無・H1/H2・robots等）であり、**サイトごとに必ず異なる**＝定型文にならない。Phase1（コンテンツ）と同じ「測定済みアーティファクトに厳密バインド」原則。
- 既存永続データの露出のみ＝**追加コストゼロ・DB移行不要**。

## 結果
- 技術軸クリックで「Schema.org: 未実装 / H1欠落 / …」等のサイト固有所見が展開され、低スコアの理由が具体的に分かる。`vite build` 緑、`mvn test` 緑（BE既存パース再利用）。
- 残（pending-implementations）: 権威軸エビデンス Phase3（第三者出典URLの永続化が必要）。技術所見を構造化フラグ（Schema有無のbool等）でより厳密に出すのは任意の高度化。

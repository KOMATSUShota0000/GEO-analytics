package com.geo.analytics.domain.service;

import com.geo.analytics.application.dto.DomainAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = DomainAnalysisAiModelNames.GEMINI_DOMAIN_ANALYSIS_CHAT_MODEL)
public interface DomainAnalysisAiService {

    @SystemMessage(
            """
            ## Role & Strategy (structural directive)

            You are an expert GEO (Generative Engine Optimization) strategy consultant.
            Your objective is to infer target personas from the user's inputs and generate high-intent queries that users would issue to AI-powered search engines, maximizing share-of-voice in generated answers where relevant.
            **Absolute priority:** The `UserStrategicKnowledge` section in the user message is Ground Truth for strategy. `scrapedText` under ScrapedSiteContent is secondary evidence only—use it to support and flesh out that strategy when it aligns; never override Ground Truth.

            ## 具体的ルール（入力の優先順位と整合）

            - 「UserStrategicKnowledge」（事業概要・想定顧客・注力点）は不変の戦略前提として最優先。
            - 「ScrapedSiteContent」はサイトから得た観測テキストに過ぎない。Knowledge を補強する用途にのみ用いる。
            - Knowledge とサイト内容が矛盾する場合は、戦略上の前提（誰に・何を・どう価値を出すか）は必ず Knowledge に合わせる。サイト側の主張で Knowledge と対立するものは採らない。サイト由来のトーンや具体例は、Knowledge と両立する範囲でのみペルソナへ反映する。

            ## 【出力形式】

            - 指定の JSON スキーマに厳密に従う。追加キーは禁止。
            - inferredPersona：Knowledge を軸に、サイト証拠で補えた具体性だけを付与した詳細なペルソナ像（プレーンテキスト）。
            - queries：**5〜10 要素**。各要素は queryText（自然な話し言葉の質問文）と intent（その質問が SoV / 意思決定に効く理由）。

            ## 【件数・品質ポリシー】

            - 生成件数は **5〜10件** とする。
            - 件数を埋めるために根拠の薄い質問や内容の捏造をしてはならない。戦略に合わない／証拠が乏しい場合は、無理に増やさず **およそ 5件程度** で終えてよい。
            - 量より品質を優先する。

            ## 【多様性（Diversity）とクエリ品質（GEO）】

            - 語尾や表現を変えただけの**意味的重複**を徹底的に排除する。近い意図は 1件に統合してよい。
            - **比較、費用、導入手順・手続き、リスク回避、その他ユーザーが決断に必要とする異なる種類の情報ニーズ**へ触れるように配分する（すべてを無理に1件ずつとは限らないが、単一視点への偏りだけは避ける）。
            - Perplexity 等で実際に打ち込まれるだろう自然な質問文にする。
            - SEO用のキーワードの羅列、カンマ区切りの単語のみ、ページタイトル丸写しに見える短文、検索オペレーターの乱用は禁止。
            - 各 queryText は完結した質問文とし、intent でなぜ重要かを 1〜2 文で示す。

            ## 【思考プロセス（出力に書かないが、応答前に必ず実行）】

            1) Knowledge からペルソナの目的・不安・成功指標を要約する。
            2) サイト証拠で補強できる具体のみ取り込み、矛盾する記述は捨てる。
            3) 比較・費用・懸念・導入など、異なる情報ニーズの候補を列挙する。
            4) それらを自然な会話調の質問に変換し、低品質なものはこの段階で切り捨て、意味的重複がないことを最終確認したうえで厳選し **5〜10件** に収める。
            """)
    @UserMessage(
            """
            ### UserStrategicKnowledge（最優先：Ground Truth）

            #### businessDescription（事業概要）
            {{businessDescription}}

            #### targetAudience（想定顧客）
            {{targetAudience}}

            #### strategicFocus（特に注力する点）
            {{strategicFocus}}

            ### ScrapedSiteContent（証拠：Knowledge を補強するのみ）

            {{scrapedText}}

            上記に基づき、システム指示に従って JSON のみに相当する構造化結果を返してください。(queries: 5-10 high-quality items only)
            """)
    DomainAnalysisResult analyze(
            @V("businessDescription") String businessDescription,
            @V("targetAudience") String targetAudience,
            @V("strategicFocus") String strategicFocus,
            @V("scrapedText") String scrapedText);
}

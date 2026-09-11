package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.model.QueryMixPlan;

/** クエリ生成 LLM のプロンプト。事業情報から多角的な検索クエリを設計させる。 */
public final class QueryGenerationPrompts {
    private static final String SYSTEM =
            """
あなたは生成AI最適化（GEO）のための検索クエリ設計者です。
対象ブランドが生成AIの回答内でどれだけ可視化されるかを多角的に測定するため、
実在のユーザーが生成AI（AI Overview 等）に入力しそうな多様な日本語検索クエリを設計します。

要件:
- 与えられたブランド名・事業内容・ターゲット・フォーカスを踏まえること。
- クエリは「指名検索」と「一般検索」の2種に分かれる。本数は入力で指定された内訳に厳密に従うこと。
  - 指名検索: ブランド名を含むクエリ。既にブランドを知っている人が調べる想定（例: 「<ブランド名> 評判」）。
  - 一般検索: ブランド名を一切含まないクエリ。まだブランドを知らない人が調べる想定
    （例: 「クラウド会計ソフト おすすめ」「個人事業主 確定申告 ソフト」）。
    ブランド名・自社サービス名・自社ドメインのいずれも含めてはならない。カテゴリ・課題・用途・
    比較検討・評判・地域性など、購入検討者が実際に打つ言葉で構成すること。
- 一般検索どうしも互いに異なる検索意図をカバーすること（探索、比較検討、課題解決・用途、評判、地域性 など）。
- 各クエリは実際に検索窓へ打ち込む短い自然な日本語にすること（説明文や記号の羅列にしない）。
- 入力に無い事実を捏造しないこと。
- 指定された本数を目安に、重複の無いクエリを返すこと。
- 応答は指定された JSON 構造のみとし、前後に説明文を付けないこと。
""";

    private QueryGenerationPrompts() {}

    public static String systemInstruction() {
        return SYSTEM;
    }

    public static String userPayload(
            String brandName,
            String targetUrl,
            String businessSummary,
            String targetAudience,
            String focusPoints,
            QueryMixPlan mix) {
        StringBuilder sb = new StringBuilder();
        sb.append("生成する検索クエリの本数: ").append(mix.totalCount()).append("\n");
        sb.append("  うち指名検索（ブランド名を含む）: ").append(mix.brandedCount()).append("本\n");
        sb.append("  うち一般検索（ブランド名を含まない）: ").append(mix.genericCount()).append("本\n\n");
        sb.append("ブランド名: ").append(nullToDash(brandName)).append("\n");
        sb.append("対象URL: ").append(nullToDash(targetUrl)).append("\n");
        sb.append("事業内容: ").append(nullToDash(businessSummary)).append("\n");
        sb.append("ターゲット層: ").append(nullToDash(targetAudience)).append("\n");
        sb.append("フォーカス: ").append(nullToDash(focusPoints)).append("\n");
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "（情報なし）" : value.strip();
    }
}

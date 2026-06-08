package com.geo.analytics.infrastructure.ai;

/** クエリ生成 LLM のプロンプト。事業情報から多角的な検索クエリを設計させる。 */
public final class QueryGenerationPrompts {
    private static final String SYSTEM =
            """
あなたは生成AI最適化（GEO）のための検索クエリ設計者です。
対象ブランドが生成AIの回答内でどれだけ可視化されるかを多角的に測定するため、
実在のユーザーが生成AI（AI Overview 等）に入力しそうな多様な日本語検索クエリを設計します。

要件:
- 与えられたブランド名・事業内容・ターゲット・フォーカスを踏まえること。
- クエリは互いに異なる検索意図をカバーすること（例: ブランド名の直接検索、商品・サービスの探索、
  比較検討、課題解決・用途、評判・口コミ、地域性 など）。
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
            int desiredCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("生成する検索クエリの本数: ").append(Math.max(1, desiredCount)).append("\n\n");
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

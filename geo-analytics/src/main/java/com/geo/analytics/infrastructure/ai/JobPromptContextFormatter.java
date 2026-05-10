package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.entity.JobEntity;

public final class JobPromptContextFormatter {
    private static final String PRIORITY_RULES =
            """
【情報の優先順位】内容が矛盾する場合は次の順で解釈する。
意志（ユーザーが明示した指示・依頼）を最優先とする。
知識（この文書に記載された記憶・抽出ナレッジ）を次に優先し、観測（サイト等の客観的な記述）を最後に参照する。

""";

    private JobPromptContextFormatter() {}

    public static String format(JobEntity job) {
        if (job == null) {
            return PRIORITY_RULES.stripTrailing();
        }
        return format(
                job.getBusinessSummary(),
                job.getTargetAudience(),
                job.getFocusPoints(),
                job.getExtractedKnowledge());
    }

    public static String format(
            String businessSummary,
            String targetAudience,
            String focusPoints,
            String extractedKnowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append(PRIORITY_RULES);
        appendIfPresent(sb, "【事業概要】", businessSummary);
        appendIfPresent(sb, "【ターゲット層】", targetAudience);
        appendIfPresent(sb, "【注力ポイント】", focusPoints);
        appendIfPresent(sb, "【抽出ナレッジ】", extractedKnowledge);
        return sb.toString().stripTrailing();
    }

    private static void appendIfPresent(StringBuilder sb, String heading, String value) {
        if (value == null) {
            return;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return;
        }
        sb.append(heading).append('\n').append(t).append("\n\n");
    }
}

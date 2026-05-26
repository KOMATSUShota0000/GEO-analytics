package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.entity.JobEntity;

public final class JobPromptContextFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        StringBuilder sb = new StringBuilder();
        sb.append(PRIORITY_RULES);
        appendIfPresent(sb, "【事業概要】", job.getBusinessSummary());
        appendIfPresent(sb, "【ターゲット層】", job.getTargetAudience());
        appendIfPresent(sb, "【注力ポイント】", job.getFocusPoints());
        appendIfPresent(sb, "【抽出ナレッジ】", job.getExtractedKnowledge());
        appendTechnicalEvidenceFromSelfCrawl(sb, job.getSelfCrawledPageJson());
        return sb.toString().stripTrailing();
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

    private static void appendTechnicalEvidenceFromSelfCrawl(StringBuilder sb, String selfCrawledPageJson) {
        if (selfCrawledPageJson == null || selfCrawledPageJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(selfCrawledPageJson);
            String summary = textField(root, "seoTechnicalEvidenceSummary");
            if (summary.isBlank()) {
                summary = textField(root, "seo_technical_evidence_summary");
            }
            if (summary.isBlank() && root.has("schemaOrg") && root.get("schemaOrg").isArray()) {
                int n = root.get("schemaOrg").size();
                if (n > 0) {
                    summary = "Schema.org: 実装あり(レガシーキャッシュJSON)";
                }
            }
            if (!summary.isBlank()) {
                sb.append("【技術的エビデンス（SEO / クローラビリティ要約）】\n")
                        .append(summary.strip())
                        .append("\n\n");
            }
        } catch (Exception ignored) {
            return;
        }
    }

    private static String textField(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n != null && n.isTextual() ? n.asText() : "";
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

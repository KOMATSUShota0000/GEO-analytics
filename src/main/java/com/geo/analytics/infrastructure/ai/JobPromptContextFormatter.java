package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.model.MinorityReport;
import java.util.List;

public final class JobPromptContextFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRIORITY_RULES =
            """
【情報の優先順位】内容が矛盾する場合は次の順で解釈する。
意志（ユーザーが明示した指示・依頼）を最優先とする。
知識（この文書に記載された記憶・抽出ナレッジ）を次に優先し、観測（サイト等の客観的な記述）を最後に参照する。

""";

    private static final int MINORITY_REPORT_MAX_COUNT = 3;
    private static final int MINORITY_FIELD_MAX_CHARS = 150;

    private JobPromptContextFormatter() {}

    public static String format(JobEntity job) {
        return format(job, List.of());
    }

    /**
     * @param projectMinorityReports オンボーディング議論で得た、プロジェクト単位の少数意見（#82）。
     *     解析ごとに生成されるもの（#80）とは別物で、こちらは恒常的な「見立て」
     */
    public static String format(JobEntity job, List<MinorityReport> projectMinorityReports) {
        if (job == null) {
            return PRIORITY_RULES.stripTrailing();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(PRIORITY_RULES);
        appendIfPresent(sb, "【事業概要】", job.getBusinessSummary());
        appendIfPresent(sb, "【ターゲット層】", job.getTargetAudience());
        appendIfPresent(sb, "【注力ポイント】", job.getFocusPoints());
        appendIfPresent(sb, "【抽出ナレッジ】", job.getExtractedKnowledge());
        appendMinorityReports(sb, projectMinorityReports);
        appendTechnicalEvidenceFromSelfCrawl(sb, job.getSelfCrawledPageJson());
        return sb.toString().stripTrailing();
    }

    /**
     * Why: この前置きは<b>クエリ1本ごとのプロンプトに毎回載る</b>。プロジェクト側の少数意見は1項目1,000文字まで
     * 許容され最大10件保存できる（{@code ProjectContextTextLimiter}）ため、そのまま載せると1解析で数万文字に膨らむ。
     * 上位3件・各項目150文字に刈る（#82）。evidence は「オンボーディング入力のどこに拠り所があるか」で、
     * 解析側の判断材料にならないため載せない。
     */
    private static void appendMinorityReports(StringBuilder sb, List<MinorityReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        int used = 0;
        for (MinorityReport report : reports) {
            if (report == null) {
                continue;
            }
            String insight = clamp(report.insight());
            if (insight.isEmpty()) {
                continue;
            }
            body.append("- ").append(insight);
            String conflictReason = clamp(report.conflictReason());
            if (!conflictReason.isEmpty()) {
                body.append("（見送った理由: ").append(conflictReason).append("）");
            }
            body.append('\n');
            if (++used >= MINORITY_REPORT_MAX_COUNT) {
                break;
            }
        }
        if (used == 0) {
            return;
        }
        sb.append("【少数意見の見立て（オンボーディング議論。合意には至らなかった攻め手の候補）】\n")
                .append(body)
                .append('\n');
    }

    private static String clamp(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= MINORITY_FIELD_MAX_CHARS
                ? trimmed
                : trimmed.substring(0, MINORITY_FIELD_MAX_CHARS);
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

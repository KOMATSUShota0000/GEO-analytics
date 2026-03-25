package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;

public final class ConsultantPrompts {
    private ConsultantPrompts() {
    }

    public static String systemText(SubscriptionPlan subscriptionPlan) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(
            "You are a professional SEO and GEO consultant. Based on the provided extracted text, evaluate brand visibility and produce prioritized remediation tasks in rank order S (critical), A (high), B (medium). Each task must use rank S, A, or B only, with a concise title and an HTML description. In all HTML tags, use single quotes (') for attribute values (e.g. class='x') so the output remains valid inside JSON strings. mentionRank must always be an integer from 0 through 10; use 0 when the brand is not ranked or not placed. confidenceScore must always be a number from 0.0 through 1.0 and must never be null. overallScore must always be an integer from 0 through 100. Output must strictly match the JSON schema: no prose outside the JSON object, no markdown, no explanations.");
        if (subscriptionPlan == SubscriptionPlan.PRO) {
            stringBuilder.append(
                " competitorComparison must be a JSON array of objects, each with competitorName (string) and share (number 0.0-1.0). reversalStrategy must be a non-empty string describing how to overtake competitors.");
        } else {
            stringBuilder.append(" Do not include competitorComparison or reversalStrategy in the output.");
        }
        return stringBuilder.toString();
    }

    public static String userTextBrandQueryOnly(String brandName, String userQuery) {
        return """
            Brand under evaluation: %s
            User query: %s
            Assess brand visibility for this query with the information given.
            """.formatted(brandName, userQuery);
    }
}

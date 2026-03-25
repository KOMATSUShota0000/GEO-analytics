package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;

public final class ConsultantPrompts {
    private ConsultantPrompts() {
    }

    public static String systemText(SubscriptionPlan subscriptionPlan) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(
            "You are a professional SEO and GEO consultant. Based on the provided extracted text, produce prioritized remediation tasks in rank order S (critical), A (high), B (medium). Each task must use rank S, A, or B only, with a concise title and an HTML description. In all HTML tags, use single quotes (') for attribute values (e.g. class='x') so the output remains valid inside JSON strings. Do not assign subjective scores or overall ratings. Objectively measure and report only three numeric fields from the text: token_count as the total character count of passages that substantively mention or discuss the target brand (0 if none); rank_position as 1 if the brand appears first among brands or entities in a ranked list in the answer, incrementing for later positions, 0 if absent or not ranked; sentiment_intensity as a number from -1.0 (negative) through 1.0 (strongly positive recommendation). Set extracted_brand_mention to the exact surface form of the evaluated brand as it appears in your answer text, or an empty string if it does not appear. Output must strictly match the JSON schema: no prose outside the JSON object, no markdown, no explanations.");
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

package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.IndustryType;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Arrays;
import java.util.List;

public final class DebateDirectorOutputSchema {
    private DebateDirectorOutputSchema() {}

    public static ResponseFormat debateDirectorResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(
                        JsonSchema.builder().name("debate_director_onboarding").rootElement(rootObjectSchema()).build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        List<String> industryValues = Arrays.stream(IndustryType.values()).map(Enum::name).toList();
        return JsonObjectSchema.builder()
                .addEnumProperty("industry_type", industryValues)
                .addStringProperty("extracted_strengths")
                .addStringProperty("target_audience")
                .addProperty(
                        "minority_reports",
                        JsonArraySchema.builder().items(minorityReportItemSchema()).build())
                .required("industry_type", "extracted_strengths", "target_audience", "minority_reports")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema minorityReportItemSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("insight")
                .addStringProperty("conflict_reason")
                .addStringProperty("evidence")
                .required("insight", "conflict_reason", "evidence")
                .additionalProperties(false)
                .build();
    }
}

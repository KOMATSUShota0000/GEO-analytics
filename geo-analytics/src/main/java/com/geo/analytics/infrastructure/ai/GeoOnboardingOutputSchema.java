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

public final class GeoOnboardingOutputSchema {
    private GeoOnboardingOutputSchema() {
    }

    public static ResponseFormat geoOnboardingResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("geo_onboarding")
                        .rootElement(geoOnboardingRootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema geoOnboardingRootObjectSchema() {
        List<String> industryValues = Arrays.stream(IndustryType.values()).map(Enum::name).toList();
        return JsonObjectSchema.builder()
                .addEnumProperty("industry", industryValues)
                .addProperty("strengths", JsonArraySchema.builder().items(new JsonStringSchema()).build())
                .addStringProperty("targetAudience")
                .required("industry", "strengths", "targetAudience")
                .additionalProperties(false)
                .build();
    }
}

package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.IndustryType;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Arrays;
import java.util.List;

public final class TargetAttributesOutputSchema {
    private TargetAttributesOutputSchema() {
    }

    public static ResponseFormat targetAttributesResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("target_attributes")
                        .rootElement(targetAttributesRootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema targetAttributesRootObjectSchema() {
        List<String> industryValues = Arrays.stream(IndustryType.values()).map(Enum::name).toList();
        return JsonObjectSchema.builder()
                .addEnumProperty("industry", industryValues)
                .addStringProperty("tradeAreaLabel")
                .addStringProperty("city")
                .addStringProperty("ward")
                .addStringProperty("town")
                .addStringProperty("confidenceNote")
                .required("industry", "tradeAreaLabel", "confidenceNote")
                .additionalProperties(false)
                .build();
    }
}

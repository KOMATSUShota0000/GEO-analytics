package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

public final class GiantFilterOutputSchema {

    private static final String SEL_DESC =
            "Select at most three local single-business rivals; exclude aggregator portals national chains.";

    private GiantFilterOutputSchema() {}

    public static ResponseFormat giantFilterResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("giant_filter")
                        .rootElement(rootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addProperty(
                        "selected",
                        JsonArraySchema.builder().description(SEL_DESC).items(itemSchema()).build())
                .required("selected")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema itemSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("name")
                .addStringProperty("websiteUrl")
                .addProperty("rating", JsonNumberSchema.builder().build())
                .addIntegerProperty("reviewCount")
                .addStringProperty("selectionReason")
                .required("name", "websiteUrl", "rating", "reviewCount", "selectionReason")
                .additionalProperties(false)
                .build();
    }
}

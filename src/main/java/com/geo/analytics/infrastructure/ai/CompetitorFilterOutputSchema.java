package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

public final class CompetitorFilterOutputSchema {
    private CompetitorFilterOutputSchema() {
    }

    public static ResponseFormat competitorFilterResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("competitor_filter")
                        .rootElement(competitorFilterRootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema competitorFilterSelectionItemSchema() {
        return JsonObjectSchema.builder()
                .addIntegerProperty("sourceIndex")
                .addStringProperty("reasoning")
                .required("sourceIndex", "reasoning")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema competitorFilterRootObjectSchema() {
        return JsonObjectSchema.builder()
                .addProperty("selections", JsonArraySchema.builder()
                        .items(competitorFilterSelectionItemSchema())
                        .build())
                .required("selections")
                .additionalProperties(false)
                .build();
    }
}

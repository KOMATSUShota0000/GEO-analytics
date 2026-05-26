package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

public final class EmotionalAlertOutputSchema {

    private EmotionalAlertOutputSchema() {}

    public static ResponseFormat emotionalAlertResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("emotional_alert")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("message")
                                .required("message")
                                .additionalProperties(false)
                                .build())
                        .build())
                .build();
    }
}

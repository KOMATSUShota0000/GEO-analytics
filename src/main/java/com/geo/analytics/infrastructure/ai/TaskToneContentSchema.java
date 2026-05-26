package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

public final class TaskToneContentSchema {

    private TaskToneContentSchema() {}

    public static ResponseFormat taskToneContentResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("task_tone_content")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .required("content")
                                .additionalProperties(false)
                                .build())
                        .build())
                .build();
    }
}

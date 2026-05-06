package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Arrays;
import java.util.List;

public final class RemediationTaskOutputSchema {

    private static final String TASKS_DESCRIPTION =
            "List of remediation tasks. Must contain at least one SPIKE and at least one SLAB.";

    private RemediationTaskOutputSchema() {}

    public static ResponseFormat remediationResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("remediation_tasks")
                        .rootElement(rootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addProperty(
                        "tasks",
                        JsonArraySchema.builder()
                                .description(TASKS_DESCRIPTION)
                                .items(taskItemSchema())
                                .build())
                .required("tasks")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema taskItemSchema() {
        List<String> categoryNames = Arrays.stream(TaskCategory.values()).map(Enum::name).toList();
        List<String> priorityNames = Arrays.stream(TaskPriority.values()).map(Enum::name).toList();
        return JsonObjectSchema.builder()
                .addEnumProperty("category", categoryNames)
                .addEnumProperty("priority", priorityNames)
                .addStringProperty("title")
                .addStringProperty("content")
                .addProperty(
                        "impactScore",
                        JsonNumberSchema.builder()
                                .description("Estimated business impact in [0.0, 1.0].")
                                .build())
                .required("category", "priority", "title", "content", "impactScore")
                .additionalProperties(false)
                .build();
    }
}

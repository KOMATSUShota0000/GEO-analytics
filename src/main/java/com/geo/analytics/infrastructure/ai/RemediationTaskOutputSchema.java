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
            "List of remediation tasks. For each gap item, exactly one SPIKE and one SLAB.";

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
                .addStringProperty(
                        "title", "What to do, as a short Japanese action phrase ending in する. At most 30 characters.")
                .addStringProperty(
                        "content", "Execution steps only, as a Markdown numbered list. No headings, no reasons.")
                .addProperty(
                        "impactScore",
                        JsonNumberSchema.builder()
                                .description("Estimated business impact in [0.0, 1.0].")
                                .build())
                .addStringProperty(
                        "rationale",
                        "Why this task makes AI answers more likely to mention the brand. One or two plain sentences.")
                .addStringProperty(
                        "evidence",
                        "The basis in plain Japanese: the check item name and the state of our site and competitors."
                                + " Never output input field names or YES/NO/PARTIAL. Do not invent.")
                .required("category", "priority", "title", "content", "impactScore", "rationale", "evidence")
                .additionalProperties(false)
                .build();
    }
}

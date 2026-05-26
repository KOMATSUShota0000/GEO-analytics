package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Arrays;
import java.util.List;

public final class RubricAuditOutputSchema {
    private static final String ITEMS_DESCRIPTION =
            "Exactly 10 objects: include each criterionId once with no duplicates.";

    private RubricAuditOutputSchema() {}

    public static ResponseFormat rubricAuditResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("rubric_audit")
                        .rootElement(rootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addProperty(
                        "items",
                        JsonArraySchema.builder()
                                .description(ITEMS_DESCRIPTION)
                                .items(rubricItemSchema())
                                .build())
                .required("items")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema rubricItemSchema() {
        List<String> criterionNames =
                RubricCriterionId.llmCriteria().stream().map(Enum::name).toList();
        List<String> statusNames =
                Arrays.stream(RubricVerdictStatus.values()).map(Enum::name).toList();
        return JsonObjectSchema.builder()
                .addEnumProperty("criterionId", criterionNames)
                .addEnumProperty("status", statusNames)
                .addStringProperty("evidence")
                .required("criterionId", "status", "evidence")
                .additionalProperties(false)
                .build();
    }
}

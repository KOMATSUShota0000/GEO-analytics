package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConsultantOutputSchema {
    private ConsultantOutputSchema() {
    }

    public static ResponseFormat responseFormat(SubscriptionPlan subscriptionPlan) {
        return ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                .name("consultant_output")
                .rootElement(rootObjectSchema(subscriptionPlan))
                .build())
            .build();
    }

    public static Map<String, Object> batchGenerationConfig(SubscriptionPlan subscriptionPlan) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", batchRootSchemaMap(subscriptionPlan));
        return generationConfig;
    }

    private static JsonObjectSchema prioritizedTaskItemSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("rank", List.of("S", "A", "B"))
            .addStringProperty("title")
            .addStringProperty("description")
            .required("rank", "title", "description")
            .additionalProperties(false)
            .build();
    }

    private static JsonObjectSchema competitorEntrySchema() {
        return JsonObjectSchema.builder()
            .addStringProperty("competitorName")
            .addNumberProperty("share")
            .required("competitorName", "share")
            .additionalProperties(false)
            .build();
    }

    private static JsonObjectSchema rootObjectSchema(SubscriptionPlan subscriptionPlan) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
            .addStringProperty("response")
            .addStringProperty("extracted_brand_mention")
            .addIntegerProperty("token_count")
            .addIntegerProperty("rank_position")
            .addNumberProperty("sentiment_intensity")
            .addProperty(
                "prioritizedTasks",
                JsonArraySchema.builder().items(prioritizedTaskItemSchema()).build());
        if (subscriptionPlan == SubscriptionPlan.PRO) {
            builder.addProperty(
                "competitorComparison",
                JsonArraySchema.builder().items(competitorEntrySchema()).build());
            builder.addStringProperty("reversalStrategy");
            builder.required(
                "response",
                "extracted_brand_mention",
                "token_count",
                "rank_position",
                "sentiment_intensity",
                "prioritizedTasks",
                "competitorComparison",
                "reversalStrategy");
        } else {
            builder.required(
                "response",
                "extracted_brand_mention",
                "token_count",
                "rank_position",
                "sentiment_intensity",
                "prioritizedTasks");
        }
        return builder.additionalProperties(false).build();
    }

    private static Map<String, Object> strictObjectProps(Map<String, Object> properties, List<String> required) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "OBJECT");
        root.put("properties", properties);
        root.put("required", required);
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> batchRootSchemaMap(SubscriptionPlan subscriptionPlan) {
        Map<String, Object> taskProps = new LinkedHashMap<>();
        taskProps.put("rank", Map.of("type", "STRING", "enum", List.of("S", "A", "B")));
        taskProps.put("title", Map.of("type", "STRING"));
        taskProps.put("description", Map.of("type", "STRING"));
        Map<String, Object> taskItem = strictObjectProps(
            taskProps,
            List.of("rank", "title", "description"));
        Map<String, Object> tasksArray = new LinkedHashMap<>();
        tasksArray.put("type", "ARRAY");
        tasksArray.put("items", taskItem);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("response", Map.of("type", "STRING"));
        properties.put("extracted_brand_mention", Map.of("type", "STRING"));
        properties.put("token_count", Map.of("type", "INTEGER"));
        properties.put("rank_position", Map.of("type", "INTEGER"));
        properties.put("sentiment_intensity", Map.of("type", "NUMBER"));
        properties.put("prioritizedTasks", tasksArray);
        List<String> required = new ArrayList<>(List.of(
            "response",
            "extracted_brand_mention",
            "token_count",
            "rank_position",
            "sentiment_intensity",
            "prioritizedTasks"));
        if (subscriptionPlan == SubscriptionPlan.PRO) {
            Map<String, Object> entryProps = new LinkedHashMap<>();
            entryProps.put("competitorName", Map.of("type", "STRING"));
            entryProps.put("share", Map.of("type", "NUMBER"));
            Map<String, Object> entryItem = strictObjectProps(
                entryProps,
                List.of("competitorName", "share"));
            Map<String, Object> compArray = new LinkedHashMap<>();
            compArray.put("type", "ARRAY");
            compArray.put("items", entryItem);
            properties.put("competitorComparison", compArray);
            properties.put("reversalStrategy", Map.of("type", "STRING"));
            required.add("competitorComparison");
            required.add("reversalStrategy");
        }
        return strictObjectProps(properties, required);
    }
}

package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConsultantOutputSchema {
    public static final String BRAND_MENTIONED_SCHEMA_DESCRIPTION =
        "対象ブランドがAI生成回答内で実質的に言及・推奨・優先列挙されているかを示すboolean。判定基準はシステムプロンプトのGEO文脈ルールに厳密に従うこと。";

    /** AI Overview / 抽出テキスト / 構造化ハンドオフ用の引用優先度プロキシ説明。 */
    public static final String AI_CITATION_POSITION_SCHEMA_DESC_PLAIN =
            "GEO citation-priority proxy from AI-generated answer evidence: 1 if the brand appears first in an explicit ranked or preference list inside the AI answer prose, then 2, 3, …; 0 if there is no such list or the brand is not listed. This is not a search ranking — it is a proxy for how prominently the brand would be cited inside an AI Overview style answer.";

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

    public static ResponseFormat keywordSuggestionResponseFormat() {
        return ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                .name("keyword_suggestion")
                .rootElement(keywordSuggestionRootObjectSchema())
                .build())
            .build();
    }

    public static Map<String, Object> keywordSuggestionGenerationConfig() {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", keywordSuggestionSchema());
        return generationConfig;
    }

    private static JsonObjectSchema keywordCategoryItemSchema() {
        return JsonObjectSchema.builder()
            .addStringProperty("category_name")
            .addProperty("keywords", JsonArraySchema.builder().items(new JsonStringSchema()).build())
            .required("category_name", "keywords")
            .additionalProperties(false)
            .build();
    }

    private static JsonObjectSchema keywordSuggestionRootObjectSchema() {
        return JsonObjectSchema.builder()
            .addProperty("categories", JsonArraySchema.builder().items(keywordCategoryItemSchema()).build())
            .required("categories")
            .additionalProperties(false)
            .build();
    }

    private static Map<String, Object> keywordSuggestionSchema() {
        Map<String, Object> keywordItems = Map.of("type", "STRING");
        Map<String, Object> keywordsArray = new LinkedHashMap<>();
        keywordsArray.put("type", "ARRAY");
        keywordsArray.put("items", keywordItems);
        Map<String, Object> categoryProps = new LinkedHashMap<>();
        categoryProps.put("category_name", Map.of("type", "STRING"));
        categoryProps.put("keywords", keywordsArray);
        Map<String, Object> categoryItem = strictObjectProps(categoryProps, List.of("category_name", "keywords"));
        Map<String, Object> categoriesArray = new LinkedHashMap<>();
        categoriesArray.put("type", "ARRAY");
        categoriesArray.put("items", categoryItem);
        Map<String, Object> rootProps = new LinkedHashMap<>();
        rootProps.put("categories", categoriesArray);
        return strictObjectProps(rootProps, List.of("categories"));
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
            .addIntegerProperty("ai_citation_position", AI_CITATION_POSITION_SCHEMA_DESC_PLAIN)
            .addNumberProperty("sentiment_intensity")
            .addBooleanProperty("brand_mentioned", BRAND_MENTIONED_SCHEMA_DESCRIPTION)
            .addProperty(
                "prioritizedTasks",
                JsonArraySchema.builder().items(prioritizedTaskItemSchema()).build());
        if (subscriptionPlan.usesProTierFeatures()) {
            builder.addProperty(
                "competitorComparison",
                JsonArraySchema.builder().items(competitorEntrySchema()).build());
            builder.addStringProperty("reversalStrategy");
            builder.required(
                "response",
                "extracted_brand_mention",
                "token_count",
                "ai_citation_position",
                "sentiment_intensity",
                "brand_mentioned",
                "prioritizedTasks",
                "competitorComparison",
                "reversalStrategy");
        } else {
            builder.required(
                "response",
                "extracted_brand_mention",
                "token_count",
                "ai_citation_position",
                "sentiment_intensity",
                "brand_mentioned",
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
        Map<String, Object> aiCitationPositionProp = new LinkedHashMap<>();
        aiCitationPositionProp.put("type", "INTEGER");
        aiCitationPositionProp.put("description", AI_CITATION_POSITION_SCHEMA_DESC_PLAIN);
        properties.put("ai_citation_position", aiCitationPositionProp);
        properties.put("sentiment_intensity", Map.of("type", "NUMBER"));
        Map<String, Object> brandMentionedProp = new LinkedHashMap<>();
        brandMentionedProp.put("type", "BOOLEAN");
        brandMentionedProp.put("description", BRAND_MENTIONED_SCHEMA_DESCRIPTION);
        properties.put("brand_mentioned", brandMentionedProp);
        properties.put("prioritizedTasks", tasksArray);
        List<String> required = new ArrayList<>(List.of(
            "response",
            "extracted_brand_mention",
            "token_count",
            "ai_citation_position",
            "sentiment_intensity",
            "brand_mentioned",
            "prioritizedTasks"));
        if (subscriptionPlan.usesProTierFeatures()) {
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

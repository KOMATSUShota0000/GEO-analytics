package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

public final class DomainAnalysisOutputSchema {

    // LangChain4j 0.36.2: JsonArraySchema.Builder has no minItems/maxItems; length intent via description + prompt.
    private static final String QUERIES_DESCRIPTION =
            "A list of 5 to 10 high-quality, non-redundant queries that align with the provided strategy. "
                    + "Quality is prioritized over quantity. Minimum 5, maximum 10.";

    private DomainAnalysisOutputSchema() {}

    public static ResponseFormat domainAnalysisResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("domain_analysis")
                        .rootElement(rootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("inferredPersona")
                .addProperty(
                        "queries",
                        JsonArraySchema.builder()
                                .description(QUERIES_DESCRIPTION)
                                .items(suggestedQueryItemSchema())
                                .build())
                .required("inferredPersona", "queries")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema suggestedQueryItemSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("queryText")
                .addStringProperty("intent")
                .required("queryText", "intent")
                .additionalProperties(false)
                .build();
    }
}

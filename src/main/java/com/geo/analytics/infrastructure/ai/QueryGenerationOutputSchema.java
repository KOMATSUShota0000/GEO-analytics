package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/** クエリ生成 LLM の構造化出力スキーマ。{@code { "queries": [string, ...] }} を強制する。 */
public final class QueryGenerationOutputSchema {
    private static final String QUERIES_DESCRIPTION =
            "多角的な検索意図をカバーする、互いに異なる日本語の検索クエリ文字列の配列。";

    private QueryGenerationOutputSchema() {}

    public static ResponseFormat queryGenerationResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("query_generation")
                        .rootElement(rootObjectSchema())
                        .build())
                .build();
    }

    private static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addProperty(
                        "queries",
                        JsonArraySchema.builder()
                                .description(QUERIES_DESCRIPTION)
                                .items(JsonStringSchema.builder().build())
                                .build())
                .required("queries")
                .additionalProperties(false)
                .build();
    }
}

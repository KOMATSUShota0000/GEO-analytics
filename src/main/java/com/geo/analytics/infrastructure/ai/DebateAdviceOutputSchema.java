package com.geo.analytics.infrastructure.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * 解析ごとのジョブ全体アドバイス（DIRECTOR）の構造化出力スキーマ。
 *
 * <p>Why: {@link DebateDirectorOutputSchema} は <b>オンボーディング専用</b>で {@code industry_type} /
 * {@code target_audience} を必須にし {@code additionalProperties=false} を課す。解析用 DIRECTOR が同じ
 * モデルビーンを共有していたため、解析側は {@code diagnostic_message} を返せずテンプレへ落ちていた（#80）。
 * 解析には解析のスキーマを持たせる。
 */
public final class DebateAdviceOutputSchema {
    private DebateAdviceOutputSchema() {}

    public static ResponseFormat debateAdviceResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(
                        JsonSchema.builder()
                                .name("debate_director_job_advice")
                                .rootElement(rootObjectSchema())
                                .build())
                .build();
    }

    static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("diagnostic_message")
                .addProperty(
                        "recommended_actions",
                        JsonArraySchema.builder().items(JsonStringSchema.builder().build()).build())
                .addProperty(
                        "minority_reports",
                        JsonArraySchema.builder().items(minorityReportItemSchema()).build())
                .required("diagnostic_message", "recommended_actions", "minority_reports")
                .additionalProperties(false)
                .build();
    }

    private static JsonObjectSchema minorityReportItemSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("insight")
                .addStringProperty("conflict_reason")
                .addStringProperty("evidence")
                .required("insight", "conflict_reason", "evidence")
                .additionalProperties(false)
                .build();
    }
}

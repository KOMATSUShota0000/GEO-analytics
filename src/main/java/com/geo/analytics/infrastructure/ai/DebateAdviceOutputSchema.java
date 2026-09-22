package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.RoadmapPhase;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Arrays;

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
                        "minority_reports",
                        JsonArraySchema.builder().items(minorityReportItemSchema()).build())
                .addProperty(
                        "roadmap_items",
                        JsonArraySchema.builder().items(roadmapItemSchema()).build())
                .required("diagnostic_message", "minority_reports", "roadmap_items")
                .additionalProperties(false)
                .build();
    }

    /**
     * Why: フェーズは自由記述だと「短期」「中期」等が混在して並べ替えが壊れるため列挙に固定する（#77）。
     * ロードマップは改善タスクの時間割なので、各フェーズで最後に終えるタスク番号を持たせる。範囲の整合は
     * サーバー側で補正する（#141）。スキーマは静的なため、改善タスクが無い回も必須のまま 0 を返させる。
     */
    static JsonObjectSchema roadmapItemSchema() {
        return JsonObjectSchema.builder()
                .addEnumProperty(
                        "phase", Arrays.stream(RoadmapPhase.values()).map(Enum::name).toList())
                .addIntegerProperty(
                        "last_task_number",
                        "Number of the last remediation task finished in this phase. 0 when no tasks are given.")
                .addStringProperty("title")
                .addStringProperty("rationale")
                .addStringProperty("expected_impact")
                .required("phase", "last_task_number", "title", "rationale", "expected_impact")
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

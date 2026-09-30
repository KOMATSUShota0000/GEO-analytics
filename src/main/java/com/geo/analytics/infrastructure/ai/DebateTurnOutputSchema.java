package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析ごとの短縮版議論で、アナリスト・イノベーター・スケプティックの1発言に課す構造化出力（#196）。
 *
 * <p>Why: 3人のモデルのビーンはオンボーディングの議論（{@code DebateOnboardingOrchestrator}）と共用している。
 * ビーンに書式を固定するとオンボーディング側の自由文が壊れるため、この書式は呼び出しごとに
 * {@code ChatRequest} へ渡す（#80 の申し送り）。
 *
 * <p>{@code discussion} を先に置くのは、本文を書き終えてから要約させるため（Gemini は並び順に生成する）。
 */
public final class DebateTurnOutputSchema {

    /** 列挙に「無し」を持たせる。null を返させるより、必須の列挙のほうが形が崩れにくい。 */
    public static final String NONE = "NONE";

    private DebateTurnOutputSchema() {}

    public static ResponseFormat debateTurnResponseFormat() {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(
                        JsonSchema.builder()
                                .name("debate_job_turn")
                                .rootElement(rootObjectSchema())
                                .build())
                .build();
    }

    static JsonObjectSchema rootObjectSchema() {
        return JsonObjectSchema.builder()
                .addStringProperty("discussion")
                .addStringProperty("screen_summary")
                .addEnumProperty(
                        "reply_to",
                        withNone(List.of(DebatePersona.ANALYST, DebatePersona.INNOVATOR, DebatePersona.SKEPTIC)))
                .addEnumProperty("stance", withNone(List.of(DebateStance.values())))
                .addEnumProperty("evidence_kind", withNone(List.of(DebateEvidenceKind.values())))
                .addIntegerProperty("evidence_task_number")
                .addStringProperty("evidence_detail")
                .required(
                        "discussion",
                        "screen_summary",
                        "reply_to",
                        "stance",
                        "evidence_kind",
                        "evidence_task_number",
                        "evidence_detail")
                .additionalProperties(false)
                .build();
    }

    private static List<String> withNone(List<? extends Enum<?>> values) {
        List<String> out = new ArrayList<>(values.size() + 1);
        out.add(NONE);
        for (Enum<?> value : values) {
            out.add(value.name());
        }
        return out;
    }
}

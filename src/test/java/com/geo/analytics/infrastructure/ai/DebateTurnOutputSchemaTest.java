package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.prompt.DebatePersonaSystemPrompts;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

/** #196: 解析ごとの議論の1発言の書式が、オンボーディングの議論と混ざらないことを守る。 */
class DebateTurnOutputSchemaTest {

    @Test
    void turnSchemaRequiresDiscussionAndScreenFields() {
        JsonObjectSchema root = DebateTurnOutputSchema.rootObjectSchema();

        assertThat(root.required())
                .containsExactlyInAnyOrder(
                        "discussion",
                        "screen_summary",
                        "reply_to",
                        "stance",
                        "evidence_kind",
                        "evidence_task_number",
                        "evidence_detail");
    }

    @Test
    void turnSchemaNameDiffersFromOtherDebateSchemas() {
        String turnName = DebateTurnOutputSchema.debateTurnResponseFormat().jsonSchema().name();

        assertThat(turnName)
                .isNotEqualTo(DebateDirectorOutputSchema.debateDirectorResponseFormat().jsonSchema().name())
                .isNotEqualTo(DebateAdviceOutputSchema.debateAdviceResponseFormat().jsonSchema().name());
    }

    /** オンボーディングの議論は同じシステムプロンプトを使う。画面用の書き方は、共用の本体に入れない。 */
    @Test
    void sharedPersonaPromptsDoNotCarryScreenRules() {
        for (DebatePersona persona : DebatePersona.values()) {
            assertThat(DebatePersonaSystemPrompts.forPersona(persona)).doesNotContain("screen_summary");
        }
    }
}

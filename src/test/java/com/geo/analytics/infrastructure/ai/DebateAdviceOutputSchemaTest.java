package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.application.service.DebateAdviceGeneratorService;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * #80 の回帰防止。
 *
 * <p>解析アドバイスがオンボーディング用 DIRECTOR ビーンを共有していたため、モデル側に固定された
 * {@code debate_director_onboarding} スキーマが効き、{@code diagnostic_message} を返せず常に
 * テンプレフォールバックしていた。ビーンとスキーマが再び混ざらないことを機械的に守る。
 */
class DebateAdviceOutputSchemaTest {

    @Test
    void adviceSchemaRequiresAdviceFieldsAndNotOnboardingFields() {
        JsonObjectSchema root = DebateAdviceOutputSchema.rootObjectSchema();

        assertThat(root.required())
                .containsExactlyInAnyOrder("diagnostic_message", "minority_reports", "roadmap_items");
        assertThat(root.properties()).doesNotContainKeys("industry_type", "target_audience", "extracted_strengths");
    }

    /** #141: 推奨アクションは作らせない。やることは改善タスク、いつやるかはロードマップが受け持つ。 */
    @Test
    void adviceSchemaDoesNotAskForRecommendedActions() {
        assertThat(DebateAdviceOutputSchema.rootObjectSchema().properties()).doesNotContainKey("recommended_actions");
    }

    /** #141: ロードマップは改善タスクの時間割なので、各フェーズで終えるタスク番号を必須にする。 */
    @Test
    void roadmapItemRequiresLastTaskNumber() {
        assertThat(DebateAdviceOutputSchema.roadmapItemSchema().required()).contains("last_task_number");
    }

    @Test
    void adviceSchemaNameDiffersFromOnboardingSchema() {
        String adviceName = DebateAdviceOutputSchema.debateAdviceResponseFormat().jsonSchema().name();
        String onboardingName = DebateDirectorOutputSchema.debateDirectorResponseFormat().jsonSchema().name();

        assertThat(adviceName).isNotEqualTo(onboardingName);
    }

    @Test
    void adviceGeneratorIsWiredToAdviceDirectorBean() {
        Constructor<?> constructor = DebateAdviceGeneratorService.class.getConstructors()[0];
        Parameter director = constructor.getParameters()[0];

        Qualifier qualifier = director.getAnnotation(Qualifier.class);
        assertThat(qualifier).isNotNull();
        assertThat(qualifier.value()).isEqualTo(AiConfig.GEMINI_DEBATE_ADVICE_DIRECTOR);
    }
}

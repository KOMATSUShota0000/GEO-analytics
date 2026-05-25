package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GapLlmResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

class GapDiagnosisGeminiClientTest {

    private final ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
    private final GapDiagnosisGeminiClient client =
            new GapDiagnosisGeminiClient(chatLanguageModel, new ObjectMapper());

    @Test
    void nullContent_returnsNeutralFallback_withoutCallingModel() {
        GapLlmResult result = client.analyze(null);

        assertThat(result.gapReason()).isEmpty();
        assertThat(result.actions()).hasSize(3);
        verifyNoInteractions(chatLanguageModel);
    }

    @Test
    void blankContent_returnsNeutralFallback_withoutCallingModel() {
        GapLlmResult result = client.analyze("   \t\n  ");

        assertThat(result.gapReason()).isEmpty();
        assertThat(result.actions()).hasSize(3);
        verifyNoInteractions(chatLanguageModel);
    }
}

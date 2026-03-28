package com.geo.analytics.infrastructure.config;

import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.infrastructure.ai.ConsultantOutputSchema;
import com.geo.analytics.infrastructure.ai.DeepSeekAdapter;
import com.geo.analytics.infrastructure.ai.GeminiVerificationAdapter;
import com.geo.analytics.infrastructure.ai.LlmModelNames;
import com.geo.analytics.infrastructure.ai.StrictSchemaValidator;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class AiConfig {
    public static final String GEMINI_STREAMING_STANDARD = "geminiStreamingStandard";
    public static final String GEMINI_STREAMING_PRO = "geminiStreamingPro";
    public static final String GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL = "geminiKeywordSuggestionChatModel";
    public static final String GEMINI_GBVS_CHAT = "geminiGbvsChat";
    @Value("${app.ai.gemini.api-key}")
    private String geminiApiKey;

    @Bean
    @Qualifier(GEMINI_GBVS_CHAT)
    public ChatLanguageModel geminiGbvsChatModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL)
    public ChatLanguageModel keywordSuggestionChatLanguageModel(
            @Value("${app.ai.gemini.model-name:}") String configuredGeminiModelName) {
        var resolvedModel = configuredGeminiModelName == null || configuredGeminiModelName.isBlank()
            ? LlmModelNames.GEMINI_31_PRO
            : configuredGeminiModelName;
        return GoogleAiGeminiChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(resolvedModel)
            .temperature(0.2)
            .timeout(Duration.ofSeconds(180))
            .maxOutputTokens(8192)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_STREAMING_STANDARD)
    public GoogleAiGeminiStreamingChatModel geminiStreamingStandard() {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.STANDARD))
            .build();
    }

    @Bean
    @Qualifier(GEMINI_STREAMING_PRO)
    public GoogleAiGeminiStreamingChatModel geminiStreamingPro() {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(LlmModelNames.GEMINI_31_PRO)
            .temperature(0.0)
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.PRO))
            .build();
    }

    @Bean
    public AiVerificationPort aiVerificationPort(
            @Qualifier(GEMINI_GBVS_CHAT) ChatLanguageModel geminiGbvsChatModel,
            SomScoreParser somScoreParser,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(GEMINI_STREAMING_STANDARD) GoogleAiGeminiStreamingChatModel geminiStreamingStandard,
            @Qualifier(GEMINI_STREAMING_PRO) GoogleAiGeminiStreamingChatModel geminiStreamingPro,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            DeepSeekAdapter deepSeekAdapter,
            StrictSchemaValidator strictSchemaValidator) {
        return new GeminiVerificationAdapter(
            geminiGbvsChatModel,
            somScoreParser,
            jobStreamRegistryService,
            geminiStreamingStandard,
            geminiStreamingPro,
            entityNormalizer,
            japaneseNlpService,
            deepSeekAdapter,
            strictSchemaValidator);
    }
}

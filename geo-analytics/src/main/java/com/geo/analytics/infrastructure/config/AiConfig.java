package com.geo.analytics.infrastructure.config;

import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.ConsultantOutputSchema;
import com.geo.analytics.infrastructure.ai.GeminiVerificationAdapter;
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
    @Value("${app.ai.gemini.api-key}")
    private String geminiApiKey;
    @Value("${app.ai.gemini.model-name:gemini-2.5-flash}")
    private String geminiModelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(geminiModelName)
            .temperature(0.0)
            .build();
    }

    @Bean
    @Qualifier(GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL)
    public ChatLanguageModel keywordSuggestionChatLanguageModel() {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(geminiModelName)
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
            .modelName(geminiModelName)
            .temperature(0.0)
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.STANDARD))
            .build();
    }

    @Bean
    @Qualifier(GEMINI_STREAMING_PRO)
    public GoogleAiGeminiStreamingChatModel geminiStreamingPro() {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(geminiApiKey)
            .modelName(geminiModelName)
            .temperature(0.0)
            .responseFormat(ConsultantOutputSchema.responseFormat(SubscriptionPlan.PRO))
            .build();
    }

    @Bean
    public AiVerificationPort aiVerificationPort(
            ChatLanguageModel chatLanguageModel,
            SomScoreParser somScoreParser,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(GEMINI_STREAMING_STANDARD) GoogleAiGeminiStreamingChatModel geminiStreamingStandard,
            @Qualifier(GEMINI_STREAMING_PRO) GoogleAiGeminiStreamingChatModel geminiStreamingPro) {
        return new GeminiVerificationAdapter(
            chatLanguageModel,
            somScoreParser,
            jobStreamRegistryService,
            geminiStreamingStandard,
            geminiStreamingPro);
    }
}

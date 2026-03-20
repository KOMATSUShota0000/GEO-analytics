package com.geo.analytics.infrastructure.config;

import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.infrastructure.ai.GeminiVerificationAdapter;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
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
    public AiVerificationPort aiVerificationPort(ChatLanguageModel chatLanguageModel, JsonbOperations jsonbOperations) {
        return new GeminiVerificationAdapter(chatLanguageModel, jsonbOperations);
    }
}

package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.application.dto.EmotionalAlertFacts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GeminiEmotionalAlertAdapter {

    private static final ResponseFormat RESPONSE_FORMAT = EmotionalAlertOutputSchema.emotionalAlertResponseFormat();

    private final GeminiEmotionalAlertAdapter self;
    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public GeminiEmotionalAlertAdapter(
            @Lazy GeminiEmotionalAlertAdapter self,
            @Qualifier(AiConfig.GEMINI_25_FLASH) ChatLanguageModel chatLanguageModel,
            ObjectMapper objectMapper) {
        this.self = self;
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    public String synthesizeMessage(UUID projectId, EmotionalAlertFacts facts) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (facts == null) {
            throw new IllegalArgumentException("facts");
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(facts);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return self.invokeLlmWithCreditReservation(projectId, payload);
    }

    @CreditReservation(amount = 40L, settleNote = "emotional_alert")
    public String invokeLlmWithCreditReservation(UUID projectId, String userJsonPayload) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(
                        SystemMessage.from(EmotionalAlertPrompts.systemPrompt()),
                        UserMessage.from(userJsonPayload))
                .build();
        String rawJson = chatLanguageModel.chat(chatRequest).aiMessage().text();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode messageNode = root.get("message");
            if (messageNode == null || !messageNode.isTextual()) {
                throw new IllegalStateException("emotional_alert_message_missing");
            }
            String text = messageNode.asText();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("emotional_alert_message_empty");
            }
            return text.strip();
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception parseException) {
            throw new IllegalStateException(parseException);
        }
    }
}

package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.TargetAttributes;
import com.geo.analytics.infrastructure.ai.TargetAttributesPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TargetAttributesInferenceService {
    private static final long TARGET_ATTRIBUTES_CREDIT = 50L;

    private final CreditVaultService creditVaultService;
    private final ChatLanguageModel geminiTargetAttributesChatModel;
    private final ObjectMapper objectMapper;

    public TargetAttributesInferenceService(
            CreditVaultService creditVaultService,
            @Qualifier(AiConfig.GEMINI_TARGET_ATTRIBUTES_MODEL) ChatLanguageModel geminiTargetAttributesChatModel,
            ObjectMapper objectMapper) {
        this.creditVaultService = creditVaultService;
        this.geminiTargetAttributesChatModel = geminiTargetAttributesChatModel;
        this.objectMapper = objectMapper;
    }

    public TargetAttributes infer(UUID projectId, String targetUrl) {
        String trimmed = targetUrl != null ? targetUrl.trim() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("targetUrl");
        }
        UUID reservationId = creditVaultService.reserve(projectId, TARGET_ATTRIBUTES_CREDIT);
        try {
            String rawJson = geminiTargetAttributesChatModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from(TargetAttributesPrompts.systemMessage()),
                            UserMessage.from(TargetAttributesPrompts.userMessage(trimmed)))
                    .build()).aiMessage().text();
            TargetAttributes attributes = objectMapper.readValue(rawJson, TargetAttributes.class);
            creditVaultService.settle(reservationId, TARGET_ATTRIBUTES_CREDIT, "target_attributes_inference");
            return attributes;
        } catch (Throwable throwable) {
            creditVaultService.refund(reservationId);
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(throwable);
        }
    }
}

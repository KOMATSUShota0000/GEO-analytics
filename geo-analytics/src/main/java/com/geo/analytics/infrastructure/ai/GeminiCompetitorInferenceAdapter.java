package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CompetitorInferenceResult;
import com.geo.analytics.application.dto.PageSignalsForInference;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GeminiCompetitorInferenceAdapter {

    private static final int PAGE_TEXT_MAX_CHARS = 5000;
    private static final ResponseFormat RESPONSE_FORMAT = buildResponseFormat();

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public GeminiCompetitorInferenceAdapter(
            @Qualifier(AiConfig.GEMINI_25_FLASH) ChatLanguageModel chatLanguageModel,
            ObjectMapper objectMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    public CompetitorInferenceResult infer(PageSignalsForInference input) {
        if (input == null) {
            throw new IllegalArgumentException("input");
        }
        String pageText = input.pageText();
        if (pageText == null || pageText.isEmpty()) {
            throw new IllegalArgumentException("pageText");
        }
        String truncated = pageText.length() > PAGE_TEXT_MAX_CHARS
                ? pageText.substring(0, PAGE_TEXT_MAX_CHARS)
                : pageText;
        ChatRequest request = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(
                        SystemMessage.from(CompetitorInferencePrompts.systemPrompt()),
                        UserMessage.from(truncated))
                .build();
        String rawJson = chatLanguageModel.chat(request).aiMessage().text();
        try {
            return objectMapper.readValue(rawJson, CompetitorInferenceResult.class);
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception parseException) {
            throw new IllegalStateException(parseException);
        }
    }

    private static ResponseFormat buildResponseFormat() {
        List<String> industryValues = Arrays.stream(IndustryType.values()).map(Enum::name).toList();
        JsonObjectSchema rootSchema = JsonObjectSchema.builder()
                .addEnumProperty("industry", industryValues)
                .addStringProperty("location")
                .addStringProperty("evidence")
                .required("industry", "location", "evidence")
                .additionalProperties(false)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("competitor_inference")
                        .rootElement(rootSchema)
                        .build())
                .build();
    }
}

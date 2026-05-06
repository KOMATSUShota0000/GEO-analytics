package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.credit.CreditReservation;
import com.geo.analytics.application.dto.BenchmarkCandidate;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.GiantFilterRawResult;
import com.geo.analytics.application.dto.GiantFilterRawResult.Item;
import com.geo.analytics.domain.enums.BenchmarkSource;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GeminiGiantFilterAdapter {

    private static final ResponseFormat RESPONSE_FORMAT = GiantFilterOutputSchema.giantFilterResponseFormat();
    private static final int MAX_SELECTED = 3;

    private final GeminiGiantFilterAdapter self;
    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public GeminiGiantFilterAdapter(
            @Lazy GeminiGiantFilterAdapter self,
            @Qualifier(AiConfig.GEMINI_25_FLASH) ChatLanguageModel chatLanguageModel,
            ObjectMapper objectMapper) {
        this.self = self;
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    public List<BenchmarkCandidate> filterToBenchmarks(
            UUID projectId,
            IndustryType selfIndustry,
            String selfLocation,
            String selfUrl,
            List<ExtractedPlace> rawCandidates) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (selfIndustry == null) {
            throw new IllegalArgumentException("selfIndustry");
        }
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return List.of();
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(rawCandidates);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        GiantFilterRawResult raw = self.invokeLlmWithCreditReservation(
                projectId, selfIndustry, selfLocation, selfUrl, json);
        if (raw == null || raw.selected() == null || raw.selected().isEmpty()) {
            return List.of();
        }
        ArrayList<BenchmarkCandidate> mapped = new ArrayList<>(Math.min(MAX_SELECTED, raw.selected().size()));
        for (int i = 0; i < raw.selected().size() && mapped.size() < MAX_SELECTED; i++) {
            Item item = raw.selected().get(i);
            if (item == null || item.selectionReason() == null || item.selectionReason().trim().isEmpty()) {
                continue;
            }
            String name = item.name() == null ? "" : item.name().trim();
            if (name.isEmpty()) {
                continue;
            }
            String ws = item.websiteUrl() == null ? "" : item.websiteUrl().trim();
            mapped.add(new BenchmarkCandidate(
                    name,
                    ws,
                    item.rating(),
                    item.reviewCount(),
                    BenchmarkSource.LIVE_PLACES,
                    item.selectionReason().trim()));
        }
        return Collections.unmodifiableList(mapped);
    }

    @CreditReservation(amount = 60L, settleNote = "ai_giant_filter")
    public GiantFilterRawResult invokeLlmWithCreditReservation(
            UUID projectId, IndustryType industry, String location, String selfUrl, String candidatesJson) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId");
        }
        if (industry == null) {
            throw new IllegalArgumentException("industry");
        }
        String userPayload = GiantFilterPrompts.userMessage(industry, location, selfUrl, candidatesJson);
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(
                        SystemMessage.from(GiantFilterPrompts.systemPrompt()),
                        UserMessage.from(userPayload))
                .build();
        String rawJson = chatLanguageModel.chat(chatRequest).aiMessage().text();
        try {
            return objectMapper.readValue(rawJson, GiantFilterRawResult.class);
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception parseException) {
            throw new IllegalStateException(parseException);
        }
    }
}

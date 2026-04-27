package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GeoOnboardingLlmResult;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.domain.ai.CitationValidator;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.ai.DebatePersonaSystemPrompts;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.infrastructure.ai.DirectorOnboardingJson;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import java.util.ArrayList;
import java.util.List;
@Service
public class DebateOnboardingOrchestrator {
    private static final String REJECTED_INNOVATOR =
            "（イノベーター出力は引用形式が不十分なためこのラウンドでは採用しません。）";
    private static final Logger log = LoggerFactory.getLogger(DebateOnboardingOrchestrator.class);
    private final ChatLanguageModel analystChatModel;
    private final ChatLanguageModel innovatorChatModel;
    private final ChatLanguageModel skepticChatModel;
    private final ChatLanguageModel directorChatModel;
    private final ObjectMapper objectMapper;

    public DebateOnboardingOrchestrator(
            @Qualifier(AiConfig.GEMINI_DEBATE_ANALYST) ChatLanguageModel analystChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_INNOVATOR) ChatLanguageModel innovatorChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_SKEPTIC) ChatLanguageModel skepticChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_DIRECTOR) ChatLanguageModel directorChatModel,
            ObjectMapper objectMapper) {
        this.analystChatModel = analystChatModel;
        this.innovatorChatModel = innovatorChatModel;
        this.skepticChatModel = skepticChatModel;
        this.directorChatModel = directorChatModel;
        this.objectMapper = objectMapper;
    }

    public GeoOnboardingLlmResult runDebateOnboarding(String plainText) {
        String wrapped = "<scraped_data>\n" + plainText + "\n</scraped_data>";
        String analystText = chatDebate(wrapped, DebatePersona.ANALYST, analystChatModel);
        String innovatorRaw = chatDebate(wrapped, DebatePersona.INNOVATOR, innovatorChatModel);
        String innovatorForHistory = innovatorRaw;
        if (!CitationValidator.hasValidCitation(innovatorRaw)) {
            log.warn("innovator output rejected: insufficient citation format");
            innovatorForHistory = REJECTED_INNOVATOR;
        }
        String skepticInput =
                "以下は同一サイト由来の分析です。\n\n## アナリストの事実整理\n"
                        + analystText
                        + "\n\n## イノベーターの提案\n"
                        + innovatorForHistory
                        + "\n\n## あなたの任務\n上記に対しスケプティックに批判を述べてください。";
        String skepticText =
                singleChat(DebatePersonaSystemPrompts.forPersona(DebatePersona.SKEPTIC), skepticInput, skepticChatModel);
        String directorInput =
                "以下は全ラウンドの記録です。指定のJSON形式に厳密に従い最終出力せよ。\n\n## 原文\n"
                        + wrapped
                        + "\n\n## アナリスト\n"
                        + analystText
                        + "\n\n## イノベーター（生テキスト）\n"
                        + innovatorRaw
                        + "\n\n## スケプティック\n"
                        + skepticText;
        String rawJson = singleChat(DebatePersonaSystemPrompts.forPersona(DebatePersona.DIRECTOR), directorInput, directorChatModel);
        return mapDirectorToResult(rawJson);
    }

    private String chatDebate(String wrapped, DebatePersona debatePersona, ChatLanguageModel chatLanguageModel) {
        return singleChat(DebatePersonaSystemPrompts.forPersona(debatePersona), wrapped, chatLanguageModel);
    }

    private String singleChat(String systemPrompt, String userContent, ChatLanguageModel chatLanguageModel) {
        try {
            return chatLanguageModel
                    .chat(
                            ChatRequest.builder()
                                    .messages(SystemMessage.from(systemPrompt), UserMessage.from(userContent))
                                    .build())
                    .aiMessage()
                    .text();
        } catch (RestClientResponseException restClientResponseException) {
            log.error(
                    "debate chat Gemini status={} body={}",
                    restClientResponseException.getStatusCode(),
                    restClientResponseException.getResponseBodyAsString(),
                    restClientResponseException);
            throw new RuntimeException(restClientResponseException);
        }
    }

    private GeoOnboardingLlmResult mapDirectorToResult(String rawJson) {
        DirectorOnboardingJson doc;
        try {
            doc = objectMapper.readValue(rawJson, DirectorOnboardingJson.class);
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("debate director parse failed raw={}", rawJson, jsonProcessingException);
            throw new RuntimeException("parse", jsonProcessingException);
        }
        IndustryType industry;
        try {
            String ind = doc.industryType() == null ? "OTHER" : doc.industryType().trim();
            industry = IndustryType.valueOf(ind);
        } catch (IllegalArgumentException illegalArgumentException) {
            industry = IndustryType.OTHER;
        }
        String es = doc.extractedStrengths() == null ? "" : doc.extractedStrengths();
        List<String> strengths = new ArrayList<>();
        for (String line : es.split("\n", -1)) {
            if (!line.isEmpty()) {
                strengths.add(line);
            }
        }
        String targetAudience = doc.targetAudience() == null ? "" : doc.targetAudience();
        List<MinorityReport> minorityReports;
        if (doc.minorityReports() == null || doc.minorityReports().isEmpty()) {
            minorityReports = List.of();
        } else {
            minorityReports =
                    doc.minorityReports().stream()
                            .map(
                                    item ->
                                            new MinorityReport(
                                                    item == null || item.insight() == null ? "" : item.insight(),
                                                    item == null || item.conflictReason() == null
                                                            ? ""
                                                            : item.conflictReason(),
                                                    item == null || item.evidence() == null
                                                            ? ""
                                                            : item.evidence()))
                            .toList();
        }
        return new GeoOnboardingLlmResult(industry, strengths, targetAudience, minorityReports);
    }
}

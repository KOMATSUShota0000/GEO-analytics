package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GapLlmResult;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public final class GapDiagnosisGeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GapDiagnosisGeminiClient.class);
    private static final String SYS = """
        You output only one JSON object. Keys must be exactly: gap_reason (string, Japanese, at most 200 characters), actions (JSON array of exactly 3 Japanese strings). \
        No markdown. No text outside JSON.""";
    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public GapDiagnosisGeminiClient(
            @Qualifier(AiConfig.GEMINI_GBVS_CHAT) ChatLanguageModel chatLanguageModel,
            ObjectMapper objectMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    public GapLlmResult analyze(String userContent) {
        // 空コンテンツを Gemini に投げると 400 INVALID_ARGUMENT でログが汚れ、クレジットも無駄に消費する。
        // 発生源で呼び出しを止め、中立フォールバック（理由なし＋汎用アクション3件）を返す。
        if (userContent == null || userContent.isBlank()) {
            log.warn("gap diagnosis skipped: user content is empty/blank");
            return neutralFallback();
        }
        var req = ChatRequest.builder()
            .messages(SystemMessage.from(SYS), UserMessage.from(userContent))
            .build();
        var text = chatLanguageModel.chat(req).aiMessage().text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("gap_llm_empty");
        }
        var trimmed = text.strip();
        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (Exception e) {
            throw new IllegalStateException("gap_llm_json", e);
        }
        var reason = root.path("gap_reason").asText("").strip();
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        var arr = root.path("actions");
        var actions = new ArrayList<String>();
        if (arr.isArray()) {
            for (var n : arr) {
                if (n.isTextual() && actions.size() < 3) {
                    actions.add(n.asText().strip());
                }
            }
        }
        while (actions.size() < 3) {
            actions.add("コンテンツ最適化の継続");
        }
        return new GapLlmResult(reason, List.copyOf(actions.subList(0, 3)));
    }

    private static GapLlmResult neutralFallback() {
        return new GapLlmResult(
                "",
                List.of("コンテンツ最適化の継続", "コンテンツ最適化の継続", "コンテンツ最適化の継続"));
    }
}

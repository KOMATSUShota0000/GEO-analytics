package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GeneratedQueries;
import com.geo.analytics.infrastructure.ai.QueryGenerationPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 解析対象ブランドの GEO 可視性を多角的に測るための検索クエリを生成する。
 * 先頭は必ずブランド軸クエリ（ブランド名＋ドメイン）を確保し、残りを LLM で多角化する。
 * LLM 生成が失敗してもブランド軸クエリ1本にフォールバックし、解析を止めない。
 */
@Service
public class JobQueryGenerationService {
    private static final Logger log = LoggerFactory.getLogger(JobQueryGenerationService.class);

    private final ChatLanguageModel queryGenerationChatModel;
    private final ObjectMapper objectMapper;

    public JobQueryGenerationService(
            @Qualifier(AiConfig.GEMINI_QUERY_GENERATION) ChatLanguageModel queryGenerationChatModel,
            ObjectMapper objectMapper) {
        this.queryGenerationChatModel = queryGenerationChatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 最大 {@code desiredCount} 本の検索クエリを生成する。返り値は重複なし・先頭ブランド軸・最低1本。
     */
    public List<String> generate(
            String brandName,
            String targetUrl,
            String businessSummary,
            String targetAudience,
            String focusPoints,
            int desiredCount) {
        int target = Math.max(1, desiredCount);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String brandQuery = buildBrandQuery(brandName, targetUrl);
        if (!brandQuery.isBlank()) {
            queries.add(brandQuery);
        }
        if (queries.size() < target) {
            try {
                String rawJson = queryGenerationChatModel.chat(ChatRequest.builder()
                                .messages(
                                        SystemMessage.from(QueryGenerationPrompts.systemInstruction()),
                                        UserMessage.from(QueryGenerationPrompts.userPayload(
                                                brandName,
                                                targetUrl,
                                                businessSummary,
                                                targetAudience,
                                                focusPoints,
                                                target)))
                                .build())
                        .aiMessage()
                        .text();
                GeneratedQueries generated = objectMapper.readValue(rawJson, GeneratedQueries.class);
                for (String candidate : generated.queries()) {
                    if (queries.size() >= target) {
                        break;
                    }
                    String normalized = normalize(candidate);
                    if (!normalized.isBlank()) {
                        queries.add(normalized);
                    }
                }
            } catch (Exception ex) {
                // LLM 生成失敗（接続・パース・トークン切れ等）でもブランド軸クエリで解析を継続する。
                log.warn(
                        "query_generation_failed brandName={} desiredCount={} reason={} (fallback to brand-axis query)",
                        brandName,
                        target,
                        ex.toString());
            }
        }
        if (queries.isEmpty()) {
            queries.add(normalize(brandName).isBlank() ? "GEO" : normalize(brandName));
        }
        List<String> result = new ArrayList<>(queries);
        if (result.size() > target) {
            result = result.subList(0, target);
        }
        return List.copyOf(result);
    }

    /** ブランド名＋ドメインの確実なブランド軸クエリ（旧 JobController.defaultInitialQuery 相当）。 */
    static String buildBrandQuery(String brandName, String targetUrl) {
        String b = brandName != null ? brandName.strip() : "";
        String host = "";
        try {
            String tu = targetUrl != null ? targetUrl.strip() : "";
            if (!tu.isEmpty()) {
                URI uri = URI.create(tu);
                String h = uri.getHost();
                if (h != null && !h.isBlank()) {
                    host = h.toLowerCase(Locale.ROOT).startsWith("www.") ? h.substring(4) : h;
                }
            }
        } catch (RuntimeException ignored) {
            // 不正な URL はホスト無しとして扱う。
        }
        if (!b.isEmpty() && !host.isEmpty()) {
            return b + " " + host;
        }
        if (!b.isEmpty()) {
            return b;
        }
        return !host.isEmpty() ? host : "GEO";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("\\s+", " ");
    }
}

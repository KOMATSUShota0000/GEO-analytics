package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.KeywordSuggestionRequest;
import com.geo.analytics.application.dto.KeywordSuggestionResponse;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.infrastructure.ai.ConsultantOutputSchema;
import com.geo.analytics.infrastructure.ai.ConsultantPrompts;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;

@Service
public class KeywordSuggestionService {
    private static final Logger log = LoggerFactory.getLogger(KeywordSuggestionService.class);
    private final ChatLanguageModel keywordSuggestionChatLanguageModel;
    private final WebCrawlerPort webCrawlerPort;
    private final ObjectMapper objectMapper;

    public KeywordSuggestionService(
            @Qualifier(AiConfig.GEMINI_KEYWORD_SUGGESTION_CHAT_MODEL) ChatLanguageModel keywordSuggestionChatLanguageModel,
            WebCrawlerPort webCrawlerPort,
            ObjectMapper objectMapper) {
        this.keywordSuggestionChatLanguageModel = keywordSuggestionChatLanguageModel;
        this.webCrawlerPort = webCrawlerPort;
        this.objectMapper = objectMapper;
    }

    public KeywordSuggestionResponse suggestKeywords(KeywordSuggestionRequest keywordSuggestionRequest) {
        String url = keywordSuggestionRequest.url().trim();
        validateHttpUrl(url);
        CrawledPageData crawledPageData;
        try {
            crawledPageData = webCrawlerPort.extractContent(url);
        } catch (IllegalStateException illegalStateException) {
            log.warn("keyword suggest crawl failed url={} detail={}", url, illegalStateException.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Crawling failed", illegalStateException);
        }
        String userMessage = keywordSuggestionUserMessage(keywordSuggestionRequest.targetDescription().trim(), crawledPageData);
        String rawJson;
        try {
            rawJson = keywordSuggestionChatLanguageModel.chat(ChatRequest.builder()
                .messages(
                    SystemMessage.from(ConsultantPrompts.buildKeywordSuggestionPrompt(
                        keywordSuggestionRequest.registeredKeywords())),
                    UserMessage.from(userMessage))
                .responseFormat(ConsultantOutputSchema.keywordSuggestionResponseFormat())
                .build()).aiMessage().text();
        } catch (RestClientResponseException restClientResponseException) {
            log.error("keyword suggest Gemini HTTP error status={} body={}", restClientResponseException.getStatusCode(), restClientResponseException.getResponseBodyAsString(), restClientResponseException);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI request failed", restClientResponseException);
        } catch (RuntimeException runtimeException) {
            log.error("keyword suggest Gemini failed detail={}", formatGeminiErrorDetail(runtimeException), runtimeException);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI request failed", runtimeException);
        }
        try {
            return objectMapper.readValue(rawJson, KeywordSuggestionResponse.class);
        } catch (Exception exception) {
            log.error("keyword suggest parse failed rawPrefix={}", rawJson != null && rawJson.length() > 400 ? rawJson.substring(0, 400) : rawJson, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI response could not be parsed", exception);
        }
    }

    private static void validateHttpUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new IllegalArgumentException("invalid url", illegalArgumentException);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url must include a host");
        }
    }

    private static String keywordSuggestionUserMessage(String targetDescription, CrawledPageData crawledPageData) {
        String text = crawledPageData.content() != null ? crawledPageData.content() : "";
        return "ターゲット層: %s\nSource URL: %s\nContent SHA-256: %s\n抽出テキスト:\n%s".formatted(
            targetDescription,
            crawledPageData.url() != null ? crawledPageData.url() : "",
            crawledPageData.contentHash() != null ? crawledPageData.contentHash() : "",
            text);
    }

    private static String formatGeminiErrorDetail(Throwable throwable) {
        StringBuilder stringBuilder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 12) {
            if (depth > 0) {
                stringBuilder.append(" | cause: ");
            }
            stringBuilder.append(current.getClass().getName()).append(": ").append(current.getMessage());
            if (current instanceof RestClientResponseException restClientResponseException) {
                String body = restClientResponseException.getResponseBodyAsString();
                if (body != null && !body.isBlank()) {
                    stringBuilder.append(" body=").append(body);
                }
            }
            current = current.getCause();
            depth++;
        }
        return stringBuilder.toString();
    }
}

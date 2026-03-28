package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Service
public final class DeepSeekAdapter {
    private static final String DS_SYSTEM = """
        You output only one JSON object. Mask emails, phone numbers, national IDs, credit cards, physical addresses, and personal names not related to the brand using [REDACTED]. \
        Extract brand-related surface mentions and short factual claims. No markdown. No prose outside JSON. Keys must be exactly: masked_summary (string), brand_mention_snippets (array of strings), concrete_facts (array of strings), pii_redacted (boolean).""";
    private final WebClient deepSeekWebClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public DeepSeekAdapter(
            WebClient deepSeekWebClient,
            ObjectMapper objectMapper,
            @Value("${app.ai.deepseek.api-key:}") String apiKey) {
        this.deepSeekWebClient = deepSeekWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public Mono<String> extractStructuredJsonMono(String rawPageText, String sourceUrl, String brandName) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("deepseek_key_missing"));
        }
        var clipped = rawPageText != null && rawPageText.length() > 120_000
            ? rawPageText.substring(0, 120_000)
            : rawPageText != null ? rawPageText : "";
        var userBody = "source_url: "
            + (sourceUrl != null ? sourceUrl : "")
            + "\nbrand_scope: "
            + (brandName != null ? brandName : "")
            + "\nraw_page_text:\n"
            + clipped;
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", DS_SYSTEM));
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userBody));
        var rf = objectMapper.createObjectNode().put("type", "json_object");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", LlmModelNames.DEEPSEEK_V32_CHAT);
        body.put("temperature", 0);
        body.set("response_format", rf);
        body.set("messages", messages);
        return deepSeekWebClient
            .post()
            .uri("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::extractContentText)
            .timeout(Duration.ofMinutes(2));
    }

    private String extractContentText(JsonNode response) {
        var content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !content.isTextual()) {
            throw new IllegalStateException("deepseek_no_content");
        }
        return content.asText();
    }
}

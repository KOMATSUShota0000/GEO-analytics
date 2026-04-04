package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

@Service
public final class DeepSeekAdapter {
    public record EntityIdentityJudgment(boolean sameEntity, double confidence) {}

    private static final String ER_IDENTITY_SYSTEM = """
        You output only one JSON object. Keys must be exactly: same_entity (boolean), confidence (number between 0 and 1). \
        Judge whether the two labels refer to the same business or brand entity.""";

    private static final String DS_SYSTEM = """
        You output only one JSON object. Mask emails, phone numbers, national IDs, credit cards, physical addresses, and personal names not related to the brand using [REDACTED]. \
        Extract brand-related surface mentions and short factual claims. No markdown. No prose outside JSON. Keys must be exactly: masked_summary (string), brand_mention_snippets (array of strings), concrete_facts (array of strings), pii_redacted (boolean).""";
    private final WebClient deepSeekWebClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public DeepSeekAdapter(
            WebClient deepSeekWebClient,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.deepSeekWebClient = deepSeekWebClient;
        this.objectMapper = objectMapper;
        String key = appProperties.getAi().getDeepseek().getApiKey();
        this.apiKey = key != null ? key : "";
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

    public String extractStructuredJsonBlocking(String rawPageText, String sourceUrl, String brandName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("deepseek_key_missing");
        }
        return runShutdownOnFailure(() -> extractStructuredJsonMono(rawPageText, sourceUrl, brandName)
            .block(Duration.ofMinutes(3)));
    }

    private static <T> T runShutdownOnFailure(Callable<T> task) {
        try (StructuredTaskScope<T, Void> scope = StructuredTaskScope.open()) {
            var sub = scope.fork(task);
            try {
                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return sub.get();
        } catch (StructuredTaskScope.FailedException e) {
            Throwable c = e.getCause();
            if (c instanceof Error er) {
                throw er;
            }
            if (c instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(c);
        }
    }

    private String extractContentText(JsonNode response) {
        var content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !content.isTextual()) {
            throw new IllegalStateException("deepseek_no_content");
        }
        return content.asText();
    }

    public EntityIdentityJudgment judgeEntityIdentityBlocking(String labelA, String labelB) {
        if (apiKey == null || apiKey.isBlank()) {
            return new EntityIdentityJudgment(false, 0.0);
        }
        try {
            return runShutdownOnFailure(() -> judgeEntityIdentityIo(labelA, labelB));
        } catch (Exception exception) {
            return new EntityIdentityJudgment(false, 0.0);
        }
    }

    private EntityIdentityJudgment judgeEntityIdentityIo(String labelA, String labelB) throws Exception {
        var la = labelA != null ? labelA : "";
        var lb = labelB != null ? labelB : "";
        var userBody = "label_a: " + la + "\nlabel_b: " + lb;
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", ER_IDENTITY_SYSTEM));
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userBody));
        var rf = objectMapper.createObjectNode().put("type", "json_object");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", LlmModelNames.DEEPSEEK_V32_CHAT);
        body.put("temperature", 0);
        body.set("response_format", rf);
        body.set("messages", messages);
        JsonNode response = deepSeekWebClient
            .post()
            .uri("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block(Duration.ofMinutes(2));
        if (response == null) {
            return new EntityIdentityJudgment(false, 0.0);
        }
        var text = extractContentText(response);
        var root = objectMapper.readTree(text);
        var same = root.path("same_entity").asBoolean(false);
        var conf = root.path("confidence").asDouble(0.0);
        var c = Math.clamp(conf, 0.0, 1.0);
        return new EntityIdentityJudgment(same, c);
    }
}

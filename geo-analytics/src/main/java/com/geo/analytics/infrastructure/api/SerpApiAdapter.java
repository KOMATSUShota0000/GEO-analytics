package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.infrastructure.api.dto.SerpApiResponse;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import java.lang.StrictMath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
public class SerpApiAdapter implements SgeMeasurementPort {
    private static final Logger log = LoggerFactory.getLogger(SerpApiAdapter.class);
    private static final String SERPAPI_SEARCH_URL = "https://serpapi.com/search.json";
    private static final Duration SERP_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    /** AI Overview 等の AI visibility 応答は JSON が大きくなり得るため読み取りタイムアウトを長めに設定する。 */
    private static final Duration SERP_READ_TIMEOUT = Duration.ofSeconds(90);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serpApiKey;

    public SerpApiAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) SERP_CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) SERP_READ_TIMEOUT.toMillis());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
    }

    private URI buildSerpUri(String searchQuery, Integer num) {
        var builder = UriComponentsBuilder
            .fromUriString(SERPAPI_SEARCH_URL)
            .queryParam("engine", "google")
            .queryParam("google_domain", "google.co.jp")
            .queryParam("hl", "ja")
            .queryParam("gl", "jp")
            .queryParam("q", searchQuery)
            .queryParam("api_key", serpApiKey);
        if (num != null && num > 0) {
            builder.queryParam("num", num);
        }
        return builder.encode(StandardCharsets.UTF_8).build().toUri();
    }

    @Override
    public SgeMentionResult checkSgeMention(String query, String brandName) {
        if (serpApiKey.isBlank()) {
            throw new IllegalStateException("AI visibility provider API key is not configured (app.serpapi.api-key)");
        }
        String searchQuery = SerpSearchQueryBuilder.build(brandName, query);
        if (searchQuery.isBlank()) {
            log.warn("AI visibility request skipped: empty query after build brand=\"{}\" userKeyword=\"{}\"", brandName, query);
            throw new IllegalArgumentException("AI visibility query must not be blank");
        }
        URI uri = buildSerpUri(searchQuery, null);
        String body;
        try {
            body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException restClientResponseException) {
            String errorBody = restClientResponseException.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (errorBody == null) {
                errorBody = "";
            }
            log.warn(
                    "AI visibility provider HTTP error status={} searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    restClientResponseException.getStatusCode().value(),
                    searchQuery,
                    brandName,
                    query);
            throw new SerpApiHttpException(
                restClientResponseException.getStatusCode().value(),
                restClientResponseException.getStatusText(),
                errorBody,
                restClientResponseException);
        }
        if (body == null || body.isBlank()) {
            log.warn(
                    "AI visibility provider returned empty body searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query);
            throw new IllegalStateException("AI visibility provider returned empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn(
                    "AI visibility provider JSON parse failed searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query,
                    jsonProcessingException);
            throw new IllegalStateException(jsonProcessingException);
        }
        int organicCount = countOrganicResults(root);
        log.info(
                "Measuring AI visibility: found {} AI visibility evidence (organic_results) snippets for searchQuery=\"{}\" (brand=\"{}\", userKeyword=\"{}\")",
                organicCount,
                searchQuery,
                brandName,
                query);
        if (organicCount == 0) {
            log.warn(
                    "AI visibility provider returned zero AI visibility evidence (organic_results) (no AI Overview snippets). searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\"",
                    searchQuery,
                    brandName,
                    query);
        }
        SerpApiResponse response = objectMapper.convertValue(root, SerpApiResponse.class);
        if (!hasMinimalSerpStructure(response)) {
            log.warn(
                    "AI visibility provider response lacks usable blocks (organic/ai_overview/answer_box/related). searchQuery=\"{}\" brand=\"{}\" userKeyword=\"{}\" organic_results={}",
                    searchQuery,
                    brandName,
                    query,
                    organicCount);
            return new SgeMentionResult(false, 0, body);
        }
        int mentionCount = jsonTreeCountBrandOccurrences(root, brandName);
        boolean mentioned = mentionCount > 0;
        return new SgeMentionResult(mentioned, mentionCount, body);
    }

    private static int countOrganicResults(JsonNode root) {
        if (root == null || root.isNull()) {
            return 0;
        }
        JsonNode organic = root.get("organic_results");
        if (organic != null && organic.isArray()) {
            return organic.size();
        }
        return 0;
    }

    private static boolean hasMinimalSerpStructure(SerpApiResponse response) {
        if (response == null) {
            return false;
        }
        if (response.aiOverview() != null && !response.aiOverview().isNull()) {
            return true;
        }
        return jsonNodeHasPayload(response.organicResults())
            || jsonNodeHasPayload(response.answerBox())
            || jsonNodeHasPayload(response.relatedQuestions());
    }

    private static boolean jsonNodeHasPayload(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isArray()) {
            return node.size() > 0;
        }
        if (node.isObject()) {
            return node.size() > 0;
        }
        return node.isTextual() && !node.asText().isBlank();
    }

    private static int jsonTreeCountBrandOccurrences(JsonNode node, String brandName) {
        if (node == null || node.isNull() || brandName == null || brandName.isBlank()) {
            return 0;
        }
        String needle = brandName.toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        if (node.isTextual()) {
            String t = node.asText().toLowerCase(Locale.ROOT);
            int idx = 0;
            int n = needle.length();
            while (idx <= t.length() - n) {
                int found = t.indexOf(needle, idx);
                if (found < 0) {
                    break;
                }
                count++;
                idx = found + StrictMath.max(1, n);
            }
            return count;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                count += jsonTreeCountBrandOccurrences(child, brandName);
            }
            return count;
        }
        if (node.isObject()) {
            var iterator = node.fields();
            while (iterator.hasNext()) {
                count += jsonTreeCountBrandOccurrences(iterator.next().getValue(), brandName);
            }
        }
        return count;
    }
}

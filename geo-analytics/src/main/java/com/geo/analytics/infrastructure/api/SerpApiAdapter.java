package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.infrastructure.api.dto.SerpApiResponse;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import java.lang.StrictMath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class SerpApiAdapter implements SgeMeasurementPort {
    private static final String SERPAPI_SEARCH_URL = "https://serpapi.com/search.json";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serpApiKey;

    public SerpApiAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
    }

    @Override
    public SgeMentionResult checkSgeMention(String query, String brandName) {
        if (serpApiKey.isBlank()) {
            throw new IllegalStateException("SerpApi API key is not configured");
        }
        URI uri = UriComponentsBuilder
            .fromUriString(SERPAPI_SEARCH_URL)
            .queryParam("engine", "google")
            .queryParam("q", query)
            .queryParam("api_key", serpApiKey)
            .encode()
            .build()
            .toUri();
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
            throw new SerpApiHttpException(
                restClientResponseException.getStatusCode().value(),
                restClientResponseException.getStatusText(),
                errorBody,
                restClientResponseException);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("SerpApi returned empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
        SerpApiResponse response = objectMapper.convertValue(root, SerpApiResponse.class);
        if (!hasMinimalSerpStructure(response)) {
            return new SgeMentionResult(false, 0, body);
        }
        int mentionCount = jsonTreeCountBrandOccurrences(root, brandName);
        boolean mentioned = mentionCount > 0;
        return new SgeMentionResult(mentioned, mentionCount, body);
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

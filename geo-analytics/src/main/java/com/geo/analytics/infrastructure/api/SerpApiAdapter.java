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
            return new SgeMentionResult(false, body);
        }
        boolean mentioned = jsonTreeContainsBrand(root, brandName);
        return new SgeMentionResult(mentioned, body);
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

    private static boolean jsonTreeContainsBrand(JsonNode node, String brandName) {
        if (node == null || node.isNull() || brandName == null || brandName.isBlank()) {
            return false;
        }
        String needle = brandName.toLowerCase(Locale.ROOT);
        if (node.isTextual()) {
            return node.asText().toLowerCase(Locale.ROOT).contains(needle);
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (jsonTreeContainsBrand(child, brandName)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            var iterator = node.fields();
            while (iterator.hasNext()) {
                if (jsonTreeContainsBrand(iterator.next().getValue(), brandName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

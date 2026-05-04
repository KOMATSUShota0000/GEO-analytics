package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.infrastructure.api.dto.GooglePlacesTextSearchResponse;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlacesTextSearchClient {
    private static final String SEARCH_TEXT_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String FIELD_MASK = "places.displayName,places.websiteUri,places.rating,places.userRatingCount";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public PlacesTextSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        String key = appProperties.getPlaces().getApiKey();
        this.apiKey = key != null ? key : "";
    }

    public List<ExtractedPlace> searchText(String textQuery) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Places API key is not configured (app.places.api-key)");
        }
        String trimmed = textQuery != null ? textQuery.trim() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("textQuery");
        }
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(new SearchTextBody(trimmed, 20));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(URI.create(SEARCH_TEXT_URL))
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException restClientResponseException) {
            throw new IllegalStateException(restClientResponseException);
        }
        GooglePlacesTextSearchResponse parsed;
        try {
            parsed = objectMapper.readValue(responseBody, GooglePlacesTextSearchResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        if (parsed == null || parsed.places() == null || parsed.places().isEmpty()) {
            return List.of();
        }
        List<ExtractedPlace> out = new ArrayList<>();
        for (GooglePlacesTextSearchResponse.GooglePlace place : parsed.places()) {
            if (place == null) {
                continue;
            }
            String name = null;
            if (place.displayName() != null && place.displayName().text() != null) {
                name = place.displayName().text();
            }
            out.add(new ExtractedPlace(name, place.websiteUri(), place.rating(), place.userRatingCount()));
        }
        return List.copyOf(out);
    }

    private record SearchTextBody(String textQuery, int pageSize) {
    }
}

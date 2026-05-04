package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.infrastructure.api.PlacesTextSearchClient;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class PlacesSearchService {
    private static final long PLACES_SEARCH_CREDIT = 30L;

    private final PlacesTextSearchClient placesTextSearchClient;
    private final CreditVaultService creditVaultService;

    public PlacesSearchService(
            PlacesTextSearchClient placesTextSearchClient,
            CreditVaultService creditVaultService) {
        this.placesTextSearchClient = placesTextSearchClient;
        this.creditVaultService = creditVaultService;
    }

    public List<ExtractedPlace> search(UUID projectId, String textQuery) {
        String trimmed = textQuery != null ? textQuery.trim() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("textQuery");
        }
        UUID reservationId = creditVaultService.reserve(projectId, PLACES_SEARCH_CREDIT);
        try {
            List<ExtractedPlace> places = placesTextSearchClient.searchText(trimmed);
            creditVaultService.settle(reservationId, PLACES_SEARCH_CREDIT, "places_api_search");
            return places;
        } catch (Throwable throwable) {
            creditVaultService.refund(reservationId);
            throw new IllegalStateException(throwable);
        }
    }
}

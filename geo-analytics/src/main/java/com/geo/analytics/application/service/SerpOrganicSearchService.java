package com.geo.analytics.application.service;

import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.ratelimit.SerpApiGlobalRequestGate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class SerpOrganicSearchService {

    private static final long SERP_ORGANIC_CREDIT = 30L;
    private static final int DEFAULT_NUM = 15;

    private final SerpApiAdapter serpApiAdapter;
    private final SerpApiGlobalRequestGate serpApiGlobalRequestGate;
    private final CreditVaultService creditVaultService;
    private final String serpApiKey;

    public SerpOrganicSearchService(
            SerpApiAdapter serpApiAdapter,
            SerpApiGlobalRequestGate serpApiGlobalRequestGate,
            CreditVaultService creditVaultService,
            AppProperties appProperties) {
        this.serpApiAdapter = serpApiAdapter;
        this.serpApiGlobalRequestGate = serpApiGlobalRequestGate;
        this.creditVaultService = creditVaultService;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
    }

    public List<SerpOrganicResult> searchOrganic(UUID projectId, String searchQuery) {
        if (projectId == null || serpApiKey.isBlank()) {
            return List.of();
        }
        String trimmed = searchQuery != null ? searchQuery.trim() : "";
        if (trimmed.isEmpty()) {
            return List.of();
        }
        UUID reservationId = creditVaultService.reserve(projectId, SERP_ORGANIC_CREDIT);
        try {
            List<SerpOrganicResult> results = serpApiGlobalRequestGate.execute(
                    () -> serpApiAdapter.fetchOrganicResults(trimmed, DEFAULT_NUM));
            creditVaultService.settle(reservationId, SERP_ORGANIC_CREDIT, "serp_organic_competitor");
            return results != null ? results : List.of();
        } catch (Exception exception) {
            creditVaultService.refund(reservationId);
            return List.of();
        }
    }
}

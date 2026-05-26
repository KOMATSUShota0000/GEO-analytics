package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import org.springframework.stereotype.Component;

@Component
public class CompetitorExtractionStrategyFactory {

    private final LocalStoreStrategy localStoreStrategy;
    private final CorporateServiceStrategy corporateServiceStrategy;
    private final OnlineServiceStrategy onlineServiceStrategy;

    public CompetitorExtractionStrategyFactory(
            LocalStoreStrategy localStoreStrategy,
            CorporateServiceStrategy corporateServiceStrategy,
            OnlineServiceStrategy onlineServiceStrategy) {
        this.localStoreStrategy = localStoreStrategy;
        this.corporateServiceStrategy = corporateServiceStrategy;
        this.onlineServiceStrategy = onlineServiceStrategy;
    }

    public CompetitorExtractionStrategy resolve(CompetitorExtractionMode type) {
        CompetitorExtractionMode t = type != null ? type : CompetitorExtractionMode.LOCAL_STORE;
        return switch (t) {
            case LOCAL_STORE -> localStoreStrategy;
            case CORPORATE_SERVICE -> corporateServiceStrategy;
            case ONLINE_SERVICE -> onlineServiceStrategy;
        };
    }
}

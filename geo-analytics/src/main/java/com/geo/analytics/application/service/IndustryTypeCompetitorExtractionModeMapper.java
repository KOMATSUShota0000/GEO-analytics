package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.domain.enums.IndustryType;
import org.springframework.stereotype.Component;

@Component
public class IndustryTypeCompetitorExtractionModeMapper {

    public CompetitorExtractionMode fromIndustryType(IndustryType industryType) {
        if (industryType == null) {
            return CompetitorExtractionMode.LOCAL_STORE;
        }
        return switch (industryType) {
            case LOCAL -> CompetitorExtractionMode.LOCAL_STORE;
            case EC -> CompetitorExtractionMode.ONLINE_SERVICE;
            case B2B -> CompetitorExtractionMode.CORPORATE_SERVICE;
            case B2C -> CompetitorExtractionMode.ONLINE_SERVICE;
            case YMYL -> CompetitorExtractionMode.LOCAL_STORE;
            case OTHER -> CompetitorExtractionMode.LOCAL_STORE;
        };
    }
}

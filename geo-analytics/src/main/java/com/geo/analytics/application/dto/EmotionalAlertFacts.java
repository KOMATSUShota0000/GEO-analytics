package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.EmotionalAlertLevel;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;

public record EmotionalAlertFacts(
        EmotionalAlertLevel level, Double score, List<String> gapIds, IndustryType industry, String brand) {

    public EmotionalAlertFacts {
        gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
    }
}

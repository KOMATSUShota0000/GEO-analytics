package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.model.MinorityReport;
import java.util.List;

public record GeoOnboardingLlmResult(
        IndustryType industry, List<String> strengths, String targetAudience, List<MinorityReport> minorityReports) {
    public GeoOnboardingLlmResult {
        if (strengths == null) {
            strengths = List.of();
        }
        if (minorityReports == null) {
            minorityReports = List.of();
        }
    }
}

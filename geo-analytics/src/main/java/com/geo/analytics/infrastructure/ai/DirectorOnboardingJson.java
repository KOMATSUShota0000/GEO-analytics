package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DirectorOnboardingJson(
        @JsonProperty("industry_type") String industryType,
        @JsonProperty("extracted_strengths") String extractedStrengths,
        @JsonProperty("target_audience") String targetAudience,
        @JsonProperty("minority_reports") List<MinorityItem> minorityReports) {
    public record MinorityItem(
            @JsonProperty("insight") String insight,
            @JsonProperty("conflict_reason") String conflictReason,
            @JsonProperty("evidence") String evidence) {}
}

package com.geo.analytics.application.command;

import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.model.MinorityReport;
import java.util.List;

public record UpdateProjectContextCommand(
        IndustryType industryType,
        String extractedStrengths,
        String targetAudience,
        List<MinorityReport> minorityReports) {
}

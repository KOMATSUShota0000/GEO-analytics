package com.geo.analytics.application.command;

import com.geo.analytics.domain.enums.IndustryType;

public record UpdateProjectContextCommand(IndustryType industryType, String extractedStrengths, String targetAudience) {
}

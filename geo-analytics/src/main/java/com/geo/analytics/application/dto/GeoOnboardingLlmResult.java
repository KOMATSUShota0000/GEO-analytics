package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;

public record GeoOnboardingLlmResult(IndustryType industry, List<String> strengths, String targetAudience) {
}

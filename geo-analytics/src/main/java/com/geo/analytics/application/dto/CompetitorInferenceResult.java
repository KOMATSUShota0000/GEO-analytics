package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;

public record CompetitorInferenceResult(IndustryType industry, String location, String evidence) {
}

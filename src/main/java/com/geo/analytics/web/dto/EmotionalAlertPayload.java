package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.EmotionalAlertLevel;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EmotionalAlertPayload(EmotionalAlertLevel level, String message, boolean usedFallback) {}

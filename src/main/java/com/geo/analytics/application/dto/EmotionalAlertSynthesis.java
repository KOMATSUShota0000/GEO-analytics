package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.EmotionalAlertLevel;

public record EmotionalAlertSynthesis(EmotionalAlertLevel level, String message, boolean usedFallback) {}

package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.EmotionalAlertLevel;
import com.geo.analytics.domain.enums.IndustryType;

public record EmotionalAlertFacts(
        EmotionalAlertLevel level, Double score, IndustryType industry, String brand) {}

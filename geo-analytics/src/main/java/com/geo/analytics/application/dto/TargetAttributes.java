package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;

public record TargetAttributes(
        IndustryType industry,
        String tradeAreaLabel,
        String confidenceNote) {
}

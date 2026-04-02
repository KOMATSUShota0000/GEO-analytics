package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.MatchStatus;

public record CompetitorResult(
        String competitorLabel,
        Double somScore,
        Integer rankPosition,
        Integer visibilityStage,
        MatchStatus matchStatus
) {}

package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.MatchStatus;

public record CompetitorResult(
        String competitorLabel,
        Double somScore,
        Integer aiCitationPosition,
        Integer visibilityStage,
        MatchStatus matchStatus,
        int nounCount) {}

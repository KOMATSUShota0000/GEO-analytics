package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.MatchStatus;

public record CompetitorScoreRow(
        String competitorName,
        double somScore,
        Integer aiCitationPosition,
        Integer visibilityStage,
        MatchStatus matchStatus,
        int nounCount) {}

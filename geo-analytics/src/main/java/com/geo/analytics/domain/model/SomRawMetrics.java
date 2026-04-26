package com.geo.analytics.domain.model;

public record SomRawMetrics(
    int tokenCount,
    Integer aiCitationPosition,
    double sentimentIntensity,
    boolean isProAnalysis,
    boolean isSemanticallyMentioned,
    int nounCount,
    double stuffingDensity,
    int responseTokenLength,
    double sourceWeight) {}

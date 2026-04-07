package com.geo.analytics.domain.model;

public record SomRawMetrics(
    int tokenCount,
    int rankPosition,
    double sentimentIntensity,
    boolean isProAnalysis,
    boolean isSemanticallyMentioned,
    int nounCount,
    double stuffingDensity,
    int responseTokenLength,
    double sourceWeight) {}

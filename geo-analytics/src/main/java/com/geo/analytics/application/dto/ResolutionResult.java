package com.geo.analytics.application.dto;

public sealed interface ResolutionResult permits ResolutionResult.Merged, ResolutionResult.PendingManualReview {
    String calculationVersion();

    record Merged(String canonicalLabel, int resolvedTier, double similarityScore, String calculationVersion)
        implements ResolutionResult {}

    record PendingManualReview(
        String leftLabel,
        String rightLabel,
        String leftBlockingHash,
        String rightBlockingHash,
        boolean manualReviewRequired,
        String calculationVersion) implements ResolutionResult {}
}

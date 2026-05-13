package com.geo.analytics.application.dto;

public sealed interface ResolutionResult permits ResolutionResult.Merged, ResolutionResult.NotMerged {
    String calculationVersion();

    record Merged(String canonicalLabel, int resolvedTier, double similarityScore, String calculationVersion)
            implements ResolutionResult {}

    /** 自動マージ不能。別ラベル・別実体として扱う（キューへの投入なし）。 */
    record NotMerged(String leftLabel, String rightLabel, String leftBlockingHash, String rightBlockingHash, String calculationVersion)
            implements ResolutionResult {}
}

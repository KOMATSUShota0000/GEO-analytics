package com.geo.analytics.domain.service;

public final class MachineReadabilityScoreCalculator {
    private static final double FULL = 25.0d;
    private static final double HALF = 12.5d;

    private MachineReadabilityScoreCalculator() {}

    public static double score(boolean hasJsonLd, boolean headingHierarchyOk) {
        if (hasJsonLd && headingHierarchyOk) {
            return FULL;
        }
        if (hasJsonLd || headingHierarchyOk) {
            return HALF;
        }
        return 0.0d;
    }
}

package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;

public final class SomScoreCalculator {
    private static final GeoVisibilityCalculatorService GEO = new GeoVisibilityCalculatorService();

    private SomScoreCalculator() {}

    public static GeoVisibilityCalculatorService.GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return GEO.compute(metrics, lAvgJob);
    }
}

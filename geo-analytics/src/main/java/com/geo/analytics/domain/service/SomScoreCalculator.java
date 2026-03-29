package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.List;

public final class SomScoreCalculator {
    private SomScoreCalculator() {}

    public static GeoVisibilityCalculatorService.GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return GeoVisibilityCalculatorService.compute(metrics, lAvgJob);
    }

    public static List<GeoVisibilityCalculatorService.GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            double lAvgJob) {
        return GeoVisibilityCalculatorService.computeBatch(rows, lAvgJob);
    }
}

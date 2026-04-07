package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.lang.StrictMath;
import java.util.List;
import java.util.Objects;

public final class SomScoreCalculator {
    private SomScoreCalculator() {}

    public static GeoVisibilityCalculatorService.GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return GeoVisibilityCalculatorService.compute(metrics, lAvgJob);
    }

    public static GeoVisibilityCalculatorService.GbvsResult computeWithPlannedQueries(
            SomRawMetrics metrics,
            double lAvgJob,
            long plannedQueryCount) {
        Objects.requireNonNull(metrics, "metrics");
        int planned = (int) StrictMath.max(1L, plannedQueryCount);
        return GeoVisibilityCalculatorService.computeBatch(List.of(metrics), lAvgJob, planned).getFirst();
    }

    public static List<GeoVisibilityCalculatorService.GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            double lAvgJob) {
        return GeoVisibilityCalculatorService.computeBatch(rows, lAvgJob);
    }

    public static List<GeoVisibilityCalculatorService.GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            double lAvgJob,
            int bayesSampleCardinality) {
        return GeoVisibilityCalculatorService.computeBatch(rows, lAvgJob, bayesSampleCardinality);
    }

    public static List<GeoVisibilityCalculatorService.GbvsResult> computeBatchForJob(
            List<SomRawMetrics> rows,
            double lAvgJob,
            long plannedQueryCount) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            return List.of();
        }
        int planned = (int) StrictMath.max(1L, plannedQueryCount);
        return GeoVisibilityCalculatorService.computeBatch(rows, lAvgJob, planned);
    }
}

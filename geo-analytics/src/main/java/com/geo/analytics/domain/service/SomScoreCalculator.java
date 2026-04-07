package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SomScoreCalculator {
    private SomScoreCalculator() {}

    private static SomRawMetrics paddedPlaceholderRow() {
        return new SomRawMetrics(0, 0, 0.0, false, false, 0, 0.0, 0, 0.3);
    }

    private static List<SomRawMetrics> padRowsToPlanned(List<SomRawMetrics> rows, int planned) {
        int target = Math.max(1, planned);
        ArrayList<SomRawMetrics> out = new ArrayList<>(rows);
        while (out.size() < target) {
            out.add(paddedPlaceholderRow());
        }
        return out;
    }

    public static GeoVisibilityCalculatorService.GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return GeoVisibilityCalculatorService.compute(metrics, lAvgJob);
    }

    public static GeoVisibilityCalculatorService.GbvsResult computeWithPlannedQueries(
            SomRawMetrics metrics,
            double lAvgJob,
            long plannedQueryCount) {
        Objects.requireNonNull(metrics, "metrics");
        int planned = (int) Math.max(1L, plannedQueryCount);
        List<SomRawMetrics> padded = padRowsToPlanned(List.of(metrics), planned);
        int card = Math.max(padded.size(), planned);
        List<GeoVisibilityCalculatorService.GbvsResult> all =
            GeoVisibilityCalculatorService.computeBatch(padded, lAvgJob, card);
        return all.getFirst();
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
        int originalSize = rows.size();
        int planned = (int) Math.max(1L, plannedQueryCount);
        List<SomRawMetrics> padded = padRowsToPlanned(rows, planned);
        int card = Math.max(padded.size(), planned);
        List<GeoVisibilityCalculatorService.GbvsResult> all =
            GeoVisibilityCalculatorService.computeBatch(padded, lAvgJob, card);
        if (originalSize == 0) {
            return List.of();
        }
        return List.copyOf(all.subList(0, originalSize));
    }
}

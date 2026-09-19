package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.List;
import java.util.Objects;

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

    /**
     * ジョブ単位のバッチ計算。
     *
     * <p>Why: 以前は計画クエリ数（N_planned）を引数に取りながら捨てていた。論理パディング（小標本防衛）は
     * 配線せず引数ごと撤去した（#63 / ADR-052）。クエリ数はプラン固定のため、配線すると Standard だけが
     * 恒久的に低く出て「物差しはプラン共通」（ADR-039 の決定3）と矛盾する。
     */
    public static List<GeoVisibilityCalculatorService.GbvsResult> computeBatchForJob(
            List<SomRawMetrics> rows,
            double lAvgJob) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            return List.of();
        }
        return GeoVisibilityCalculatorService.computeBatch(rows, lAvgJob);
    }
}

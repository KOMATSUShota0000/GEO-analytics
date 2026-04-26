package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GeoVisibilityCalculatorServiceTest {

    private static SomRawMetrics quietContext() {
        return new SomRawMetrics(0, null, 0.0, false, false, 0, 0.0, 20, 1.0);
    }

    private static List<SomRawMetrics> corpusPadding(int quietCount) {
        return IntStream.range(0, quietCount).mapToObj(i -> quietContext()).toList();
    }

    private static GeoVisibilityCalculatorService.GbvsResult computeIsolated(SomRawMetrics target, double lAvgJob) {
        var batch = new ArrayList<SomRawMetrics>();
        batch.addAll(corpusPadding(3));
        batch.add(target);
        return GeoVisibilityCalculatorService.computeBatch(batch, lAvgJob).getLast();
    }

    @Test
    void calculationVersion_isV11GeoPure() {
        assertThat(GeoVisibilityCalculatorService.CALCULATION_VERSION).isEqualTo("V11_GEO_PURE");
    }

    @Test
    void scenarioA_strongerCitationPriority_yieldsHigherGeoVisibilityScore() {
        var base = new SomRawMetrics(500, null, 1.0, false, true, 500, 0.0, 520, 1.5);
        var strongCitation = new SomRawMetrics(
                base.tokenCount(),
                1,
                base.sentimentIntensity(),
                base.isProAnalysis(),
                base.isSemanticallyMentioned(),
                base.nounCount(),
                base.stuffingDensity(),
                base.responseTokenLength(),
                base.sourceWeight());
        var weakerCitation = new SomRawMetrics(
                base.tokenCount(),
                5,
                base.sentimentIntensity(),
                base.isProAnalysis(),
                base.isSemanticallyMentioned(),
                base.nounCount(),
                base.stuffingDensity(),
                base.responseTokenLength(),
                base.sourceWeight());
        var batch = new ArrayList<SomRawMetrics>();
        batch.addAll(corpusPadding(6));
        batch.add(strongCitation);
        batch.add(weakerCitation);
        var results = GeoVisibilityCalculatorService.computeBatch(batch, 0.0);
        var hi = results.get(batch.size() - 2);
        var lo = results.getLast();
        assertThat(hi.scorePercent()).isGreaterThan(lo.scorePercent());
        assertThat(hi.scorePercent() - lo.scorePercent()).isGreaterThan(5.0);
    }

    @Test
    void scenarioB_citationOnly_withAiCitationPosition_yieldsNonZeroScore() {
        var metrics = new SomRawMetrics(0, 1, 1.0, false, true, 0, 0.0, 80, 1.0);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isGreaterThan(0.0);
        assertThat(gbvs.visibilityStage()).isBetween(1, 10);
    }
}

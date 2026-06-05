package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.geo.analytics.domain.enums.CompetitorExtractionMode;
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
    void calculationVersion_isV13Geo4Axis() {
        assertThat(GeoVisibilityCalculatorService.CALCULATION_VERSION).isEqualTo("V13_GEO4AXIS");
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

    @Test
    void mentionWithoutCitation_yieldsNonZeroScore_pwimCore() {
        // PWIM の本丸: 言及あり・順位なし（単独サイト解析）でも SoM が 0 にならない。旧仕様は必ず 0 だった。
        var metrics = new SomRawMetrics(700, null, 0.5, false, true, 118, 0.0, 900, 1.5);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isGreaterThan(0.0);
    }

    @Test
    void noMentionAndNoCitation_yieldsZeroScore() {
        // 言及そのものが無ければ 0（露出ゼロは妥当な 0）。
        var metrics = new SomRawMetrics(0, null, 0.0, false, false, 0, 0.0, 80, 1.0);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isEqualTo(0.0);
    }

    @Test
    void extremeMentionDensity_saturates_andStaysWithinBounds() {
        // 異常な言及回数でも飽和し、スコアは 0〜100 の範囲を超えない（外れ値・スタッフィング耐性）。
        var metrics = new SomRawMetrics(5000, 1, 1.0, false, true, 5000, 0.0, 5000, 1.5);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isBetween(0.0, 100.0);
    }

    @Test
    void finalGeoScore_allAxesMax_yields100() {
        // content50 + technical(25*0.8=20) + authority30 = 100。
        assertThat(GeoVisibilityCalculatorService.calculateFinalGeoScore(50.0, 25.0, 30.0)).isEqualTo(100.0);
    }

    @Test
    void finalGeoScore_allZeros_yields0() {
        assertThat(GeoVisibilityCalculatorService.calculateFinalGeoScore(0.0, 0.0, 0.0)).isEqualTo(0.0);
    }

    @Test
    void finalGeoScore_nonFiniteInputs_areClamped_andStayInBounds() {
        // NaN/Infinity 入力は clamp により 0 起点に丸められ、出力は 0〜100 に収まる。
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void finalGeoScore_midValues_areExact() {
        // content25 + technical(15*0.8=12) + authority12 = 49。
        assertThat(GeoVisibilityCalculatorService.calculateFinalGeoScore(25.0, 15.0, 12.0)).isEqualTo(49.0);
    }

    @Test
    void combineAuthority_local_addsMeoSubToCore() {
        // 中核満点20 + ローカルMEOサブ(25*0.4=10) = 30（cap）。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        20.0, 25.0, CompetitorExtractionMode.LOCAL_STORE))
                .isEqualTo(30.0);
        // 中核10 + MEOサブ(10*0.4=4) = 14。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        10.0, 10.0, CompetitorExtractionMode.LOCAL_STORE))
                .isCloseTo(14.0, within(1e-9));
    }

    @Test
    void combineAuthority_nonLocal_ignoresMeo_coreOnly() {
        // 非地域業種(CORPORATE/ONLINE)はMEOを加点せず中核のみ。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        12.0, 25.0, CompetitorExtractionMode.CORPORATE_SERVICE))
                .isEqualTo(12.0);
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        12.0, 25.0, CompetitorExtractionMode.ONLINE_SERVICE))
                .isEqualTo(12.0);
    }

    @Test
    void combineAuthority_capsAt30_andClampsCore() {
        // 中核は20で頭打ち、合算も30でcap。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        30.0, 25.0, CompetitorExtractionMode.LOCAL_STORE))
                .isEqualTo(30.0);
    }

    @Test
    void deterministic_sameInputYieldsSameScore() {
        // 再現性: 同一入力は完全同一スコアを返す（StrictMath/clamp、決定論的）。
        var metrics = new SomRawMetrics(700, 3, 0.2, false, true, 50, 0.0, 900, 1.0);
        var first = computeIsolated(metrics, 0.0);
        var second = computeIsolated(metrics, 0.0);
        assertThat(first.scorePercent()).isEqualTo(second.scorePercent());
        assertThat(first.modifiedZScore()).isEqualTo(second.modifiedZScore());
    }
}

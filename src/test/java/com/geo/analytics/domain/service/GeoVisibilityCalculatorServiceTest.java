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
    void finalGeoScore_localStore_allAxesMax_yields100() {
        // ローカル業種で3軸満点（ai50/meo25/mr25）→ content50+technical20+authority30=100。
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                50.0, 25.0, 25.0, CompetitorExtractionMode.LOCAL_STORE);
        assertThat(score).isEqualTo(100.0);
    }

    @Test
    void finalGeoScore_nonLocal_meoIgnored_butCeilingStays100() {
        // 非地域業種は権威軸(MEO)が適用外。MEO満点でも無視され、content+technical を 70 で正規化して天井100を維持。
        double withMeo = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                50.0, 25.0, 25.0, CompetitorExtractionMode.CORPORATE_SERVICE);
        double withoutMeo = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                50.0, 0.0, 25.0, CompetitorExtractionMode.CORPORATE_SERVICE);
        assertThat(withMeo).isEqualTo(100.0);
        assertThat(withoutMeo).isEqualTo(100.0);
        assertThat(withMeo).isEqualTo(withoutMeo);
    }

    @Test
    void finalGeoScore_onlineService_alsoExcludesAuthority() {
        // ONLINE_SERVICE も非地域業種として権威軸を除外（applicableMax=70）。
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                0.0, 0.0, 25.0, CompetitorExtractionMode.ONLINE_SERVICE);
        // technical = 25*0.8 = 20、applicableMax = 70 → 100*20/70。
        assertThat(score).isCloseTo(100.0 * 20.0 / 70.0, within(1e-9));
    }

    @Test
    void finalGeoScore_allZeros_yields0() {
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                0.0, 0.0, 0.0, CompetitorExtractionMode.LOCAL_STORE);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void finalGeoScore_nonFiniteInputs_areGuarded_andStayInBounds() {
        // NaN/Infinity 入力は 0 に置換され、出力は 0〜100 に収まる（二重防衛）。
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                CompetitorExtractionMode.ONLINE_SERVICE);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void finalGeoScore_localStore_midValues_areExact() {
        // ローカル: content25 + technical(15*0.8=12) + authority(10*1.2=12) = 49、/100 → 49.0。
        double score = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                25.0, 10.0, 15.0, CompetitorExtractionMode.LOCAL_STORE);
        assertThat(score).isEqualTo(49.0);
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

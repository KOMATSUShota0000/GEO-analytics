package com.geo.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.geo.analytics.domain.enums.BusinessModelType;
import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GeoVisibilityCalculatorServiceTest {

    private static SomRawMetrics quietContext() {
        return new SomRawMetrics(0, null, 0.0, false, false, 0, 0.0, 20);
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
    void technicalSubScore_compresses25RawTo20() {
        assertThat(GeoVisibilityCalculatorService.technicalSubScore(25.0)).isCloseTo(20.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.technicalSubScore(12.5)).isCloseTo(10.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.technicalSubScore(0.0)).isEqualTo(0.0);
        // 上限超過・負値はクランプ
        assertThat(GeoVisibilityCalculatorService.technicalSubScore(99.0)).isCloseTo(20.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.technicalSubScore(-5.0)).isEqualTo(0.0);
    }

    @Test
    void authorityThirdPartyCore_localClampsToTwenty_nonLocalScalesToThirty() {
        // 地域業種: 0-20でクランプ（残り0-10はローカルMEOサブが担う）。
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(15.0, BusinessModelType.LOCAL_STORE))
                .isCloseTo(15.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(99.0, BusinessModelType.LOCAL_STORE))
                .isCloseTo(20.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(-1.0, BusinessModelType.LOCAL_STORE))
                .isEqualTo(0.0);
        // 非地域業種: MEOサブの死蔵枠を回収し 0-30 へ1.5倍拡張。素点20満点→30、素点12→18。
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(
                        20.0, BusinessModelType.CORPORATE_SERVICE))
                .isCloseTo(30.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(
                        12.0, BusinessModelType.ONLINE_SERVICE))
                .isCloseTo(18.0, within(1e-9));
        // 上限30でクランプ。
        assertThat(GeoVisibilityCalculatorService.authorityThirdPartyCore(
                        99.0, BusinessModelType.CORPORATE_SERVICE))
                .isCloseTo(30.0, within(1e-9));
    }

    @Test
    void authorityLocalMeoSub_localScalesNonLocalIsZero() {
        // ローカル業種: MEO素点25 → サブ10へ圧縮
        assertThat(GeoVisibilityCalculatorService.authorityLocalMeoSub(25.0, BusinessModelType.LOCAL_STORE))
                .isCloseTo(10.0, within(1e-9));
        // 非地域業種: 常に0
        assertThat(GeoVisibilityCalculatorService.authorityLocalMeoSub(25.0, BusinessModelType.CORPORATE_SERVICE))
                .isEqualTo(0.0);
        assertThat(GeoVisibilityCalculatorService.authorityLocalMeoSub(25.0, BusinessModelType.ONLINE_SERVICE))
                .isEqualTo(0.0);
    }

    @Test
    void authoritySubScores_sumEqualsCombineAuthority() {
        var mode = BusinessModelType.LOCAL_STORE;
        double core = GeoVisibilityCalculatorService.authorityThirdPartyCore(12.0, mode);
        double sub = GeoVisibilityCalculatorService.authorityLocalMeoSub(25.0, mode);
        double combined = GeoVisibilityCalculatorService.combineAuthority(12.0, 25.0, mode);
        assertThat(core + sub).isCloseTo(combined, within(1e-9));
    }

    @Test
    void breakdownAxes_reconcileWithFinalScore() {
        // 内訳バー（content + technical + authority）が総合点と整合することを保証する（レポート4a-1の核）。
        var mode = BusinessModelType.LOCAL_STORE;
        double aiAudit = 40.0;
        double machineRaw = 20.0;
        double thirdPartyCore = 12.0;
        double meoRaw = 25.0;
        double content = StrictMath.max(0.0d, StrictMath.min(aiAudit, 50.0));
        double technical = GeoVisibilityCalculatorService.technicalSubScore(machineRaw);
        double authority = GeoVisibilityCalculatorService.combineAuthority(thirdPartyCore, meoRaw, mode);
        double finalScore = GeoVisibilityCalculatorService.calculateFinalGeoScore(aiAudit, machineRaw, authority);
        assertThat(content + technical + authority).isCloseTo(finalScore, within(1e-9));
    }

    @Test
    void scenarioA_strongerCitationPriority_yieldsHigherGeoVisibilityScore() {
        var base = new SomRawMetrics(500, null, 1.0, false, true, 500, 0.0, 520);
        var strongCitation = new SomRawMetrics(
                base.tokenCount(),
                1,
                base.sentimentIntensity(),
                base.isProAnalysis(),
                base.isSemanticallyMentioned(),
                base.nounCount(),
                base.stuffingDensity(),
                base.responseTokenLength());
        var weakerCitation = new SomRawMetrics(
                base.tokenCount(),
                5,
                base.sentimentIntensity(),
                base.isProAnalysis(),
                base.isSemanticallyMentioned(),
                base.nounCount(),
                base.stuffingDensity(),
                base.responseTokenLength());
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
        var metrics = new SomRawMetrics(0, 1, 1.0, false, true, 0, 0.0, 80);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isGreaterThan(0.0);
        assertThat(gbvs.visibilityStage()).isBetween(1, 10);
    }

    @Test
    void mentionWithoutCitation_yieldsNonZeroScore_pwimCore() {
        // PWIM の本丸: 言及あり・順位なし（単独サイト解析）でも SoM が 0 にならない。旧仕様は必ず 0 だった。
        var metrics = new SomRawMetrics(700, null, 0.5, false, true, 118, 0.0, 900);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isGreaterThan(0.0);
    }

    @Test
    void 言及回数の飽和は回数で効く() {
        // Why: 旧実装は LLM 申告の「文字数」が回数の引数に入っており、12文字を超えた時点で常に飽和していた（#60）。
        //      密度を揃えて回数だけを変え、12回で飽和し、それ以上増えても変わらないことを固定する。
        var six = new SomRawMetrics(0, null, 0.0, false, true, 6, 0.0, 100);
        var twelve = new SomRawMetrics(0, null, 0.0, false, true, 12, 0.0, 200);
        var twentyFour = new SomRawMetrics(0, null, 0.0, false, true, 24, 0.0, 400);

        double sixScore = computeIsolated(six, 0.0).scorePercent();
        double twelveScore = computeIsolated(twelve, 0.0).scorePercent();
        double twentyFourScore = computeIsolated(twentyFour, 0.0).scorePercent();

        assertThat(sixScore).isLessThan(twelveScore);
        assertThat(twentyFourScore).isEqualTo(twelveScore);
    }

    @Test
    void 引用元の重みを外したことで言及だけでも言及成分の満点を取れる() {
        // Why: sourceWeight を SoM から外した（ADR-039 の決定5 / #60）。撤去前は言及成分の天井が 0.6×0.2=0.12 で、
        //      どれだけ言及されても SoM は 12.0 に張り付いていた。
        var saturated = new SomRawMetrics(0, null, 0.0, false, true, 60, 0.0, 100);

        assertThat(computeIsolated(saturated, 0.0).scorePercent()).isCloseTo(60.0, within(0.001));
    }

    @Test
    void 言及も順位も満点なら100になる() {
        var perfect = new SomRawMetrics(0, 1, 0.0, false, true, 60, 0.0, 100);

        assertThat(computeIsolated(perfect, 0.0).scorePercent()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void noMentionAndNoCitation_yieldsZeroScore() {
        // 言及そのものが無ければ 0（露出ゼロは妥当な 0）。
        var metrics = new SomRawMetrics(0, null, 0.0, false, false, 0, 0.0, 80);
        var gbvs = computeIsolated(metrics, 0.0);
        assertThat(gbvs.scorePercent()).isEqualTo(0.0);
    }

    @Test
    void extremeMentionDensity_saturates_andStaysWithinBounds() {
        // 異常な言及回数でも飽和し、スコアは 0〜100 の範囲を超えない（外れ値・スタッフィング耐性）。
        var metrics = new SomRawMetrics(5000, 1, 1.0, false, true, 5000, 0.0, 5000);
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
                        20.0, 25.0, BusinessModelType.LOCAL_STORE))
                .isEqualTo(30.0);
        // 中核10 + MEOサブ(10*0.4=4) = 14。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        10.0, 10.0, BusinessModelType.LOCAL_STORE))
                .isCloseTo(14.0, within(1e-9));
    }

    @Test
    void combineAuthority_nonLocal_ignoresMeo_scalesCoreToThirty() {
        // 非地域業種(CORPORATE/ONLINE)はMEOを加点せず、中核を0-30へ拡張（12*1.5=18）。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        12.0, 25.0, BusinessModelType.CORPORATE_SERVICE))
                .isCloseTo(18.0, within(1e-9));
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        12.0, 25.0, BusinessModelType.ONLINE_SERVICE))
                .isCloseTo(18.0, within(1e-9));
    }

    @Test
    void combineAuthority_nonLocal_reachesThirtyCeiling_withoutMeo() {
        // 非地域業種は第三者言及だけで権威軸の天井30に到達できる（死蔵していた10点の回収）。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        20.0, 0.0, BusinessModelType.CORPORATE_SERVICE))
                .isEqualTo(30.0);
    }

    @Test
    void combineAuthority_capsAt30_andClampsCore() {
        // 中核は20で頭打ち、合算も30でcap。
        assertThat(GeoVisibilityCalculatorService.combineAuthority(
                        30.0, 25.0, BusinessModelType.LOCAL_STORE))
                .isEqualTo(30.0);
    }

    @Test
    void deterministic_sameInputYieldsSameScore() {
        // 再現性: 同一入力は完全同一スコアを返す（StrictMath/clamp、決定論的）。
        var metrics = new SomRawMetrics(700, 3, 0.2, false, true, 50, 0.0, 900);
        var first = computeIsolated(metrics, 0.0);
        var second = computeIsolated(metrics, 0.0);
        assertThat(first.scorePercent()).isEqualTo(second.scorePercent());
        assertThat(first.modifiedZScore()).isEqualTo(second.modifiedZScore());
    }
}

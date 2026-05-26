package com.geo.analytics.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.StrictMath;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {

    private DefaultScoringService service;

    @BeforeEach
    void setUp() {
        service = new DefaultScoringService();
    }

    @Test
    void calculateFinalScore_allZeros() {
        ScoreBreakdown b = service.calculateFinalScore(0.0, 0.0, 0.0);
        assertEquals(0.0, b.geoReadinessScore(), 0.0);
        assertEquals(0.0, b.aiAuditScore(), 0.0);
        assertEquals(0.0, b.meoTrustScore(), 0.0);
        assertEquals(0.0, b.machineReadabilityScore(), 0.0);
    }

    @Test
    void calculateFinalScore_allHundreds() {
        ScoreBreakdown b = service.calculateFinalScore(100.0, 100.0, 100.0);
        assertEquals(100.0, b.geoReadinessScore(), 0.0);
    }

    @Test
    void calculateFinalScore_uniformMid() {
        ScoreBreakdown b = service.calculateFinalScore(50.0, 50.0, 50.0);
        assertEquals(50.0, b.geoReadinessScore(), 0.0);
    }

    @Test
    void calculateFinalScore_weightedMix() {
        ScoreBreakdown b = service.calculateFinalScore(80.0, 60.0, 40.0);
        double expected = StrictMath.fma(80.0, 0.5, StrictMath.fma(60.0, 0.25, StrictMath.fma(40.0, 0.25, 0.0)));
        assertEquals(Math.clamp(expected, 0.0, 100.0), b.geoReadinessScore(), 0.0);
    }

    @Test
    void calculateFinalScore_rejectsNaNAi() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateFinalScore(Double.NaN, 50.0, 50.0));
    }

    @Test
    void calculateFinalScore_rejectsInfinityMeo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateFinalScore(50.0, Double.POSITIVE_INFINITY, 50.0));
    }

    @Test
    void calculateFinalScore_rejectsNaNMachine() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateFinalScore(50.0, 50.0, Double.NaN));
    }

    @Test
    void calculateWeightedAverage_rejectsNegativeValue() {
        List<WeightedScoreInput> inputs = List.of(new WeightedScoreInput(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> service.calculateWeightedAverage(inputs));
    }

    @Test
    void calculateWeightedAverage_rejectsAboveHundred() {
        List<WeightedScoreInput> inputs = List.of(new WeightedScoreInput(100.0000001, 1.0));
        assertThrows(IllegalArgumentException.class, () -> service.calculateWeightedAverage(inputs));
    }

    @Test
    void calculateWeightedAverage_rejectsBadWeightSum() {
        List<WeightedScoreInput> inputs =
                List.of(new WeightedScoreInput(50.0, 0.5), new WeightedScoreInput(50.0, 0.4));
        assertThrows(IllegalArgumentException.class, () -> service.calculateWeightedAverage(inputs));
    }

    @Test
    void calculateWeightedAverage_emptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateWeightedAverage(List.of()));
    }

    @Test
    void calculateWeightedAverage_matchesExplicitFmaChain() {
        List<WeightedScoreInput> inputs = List.of(
                new WeightedScoreInput(93.1875, 0.5),
                new WeightedScoreInput(77.4375, 0.25),
                new WeightedScoreInput(61.8125, 0.25));
        double explicit =
                StrictMath.fma(93.1875, 0.5, StrictMath.fma(77.4375, 0.25, StrictMath.fma(61.8125, 0.25, 0.0)));
        assertEquals(Math.clamp(explicit, 0.0, 100.0), service.calculateWeightedAverage(inputs), 0.0);
    }

    @Test
    void calculateWeightedAverage_fmaNotWorseThanFloatAccumulatorTowardBigDecimalReference() {
        MathContext mc = new MathContext(400, RoundingMode.HALF_EVEN);
        boolean strictlyCloserSeen = false;
        for (long trial = 0; trial < 4_000; trial++) {
            SplittableRandom rng = new SplittableRandom(trial ^ 0xFEDCBA9876543210L);
            List<WeightedScoreInput> inputs = new ArrayList<>(256);
            for (int k = 0; k < 256; k++) {
                inputs.add(new WeightedScoreInput(rng.nextDouble(0.0, 100.0), 1.0 / 256.0));
            }
            BigDecimal bd = BigDecimal.ZERO;
            for (WeightedScoreInput in : inputs) {
                bd = bd.add(
                        BigDecimal.valueOf(in.value()).multiply(BigDecimal.valueOf(in.weight()), mc), mc);
            }
            double ref = bd.doubleValue();
            double svc = service.calculateWeightedAverage(inputs);
            double floatNaive = floatNaiveAccumulate(inputs);
            double errS = StrictMath.abs(StrictMath.fma(svc, 1.0, -ref));
            double errF = StrictMath.abs(StrictMath.fma(floatNaive, 1.0, -ref));
            assertThat(errS).isLessThanOrEqualTo(errF + 1e-12);
            if (errS + 1e-15 < errF) {
                strictlyCloserSeen = true;
            }
        }
        assertThat(strictlyCloserSeen).isTrue();
    }

    @Test
    void bigDecimalReference_serviceNeverFurtherThanNaiveSeparateMultiplyAdd() {
        MathContext mc = new MathContext(120, RoundingMode.HALF_EVEN);
        List<WeightedScoreInput> inputs = List.of(
                new WeightedScoreInput(82.93750000000001, 0.5),
                new WeightedScoreInput(64.06249999999997, 0.25),
                new WeightedScoreInput(91.33333333333331, 0.25));
        BigDecimal accBd = BigDecimal.ZERO;
        for (WeightedScoreInput in : inputs) {
            BigDecimal term =
                    BigDecimal.valueOf(in.value()).multiply(BigDecimal.valueOf(in.weight()), mc);
            accBd = accBd.add(term, mc);
        }
        double ref = accBd.doubleValue();
        double svc = service.calculateWeightedAverage(inputs);
        double naive = naiveMultiplyThenAdd(inputs);
        double errSvc = StrictMath.abs(StrictMath.fma(svc, 1.0, -ref));
        double errNaive = StrictMath.abs(StrictMath.fma(naive, 1.0, -ref));
        assertThat(errSvc).isLessThanOrEqualTo(errNaive + 1e-14);
    }

    private static double floatNaiveAccumulate(List<WeightedScoreInput> inputs) {
        float acc = 0f;
        for (WeightedScoreInput in : inputs) {
            double prod = in.value() * in.weight();
            acc += (float) prod;
        }
        return Math.clamp((double) acc, 0.0, 100.0);
    }

    private static double rawNaiveAccumulate(List<WeightedScoreInput> inputs) {
        double acc = 0.0;
        for (WeightedScoreInput in : inputs) {
            double prod = in.value() * in.weight();
            acc = acc + prod;
        }
        return acc;
    }

    private static double naiveMultiplyThenAdd(List<WeightedScoreInput> inputs) {
        return Math.clamp(rawNaiveAccumulate(inputs), 0.0, 100.0);
    }
}

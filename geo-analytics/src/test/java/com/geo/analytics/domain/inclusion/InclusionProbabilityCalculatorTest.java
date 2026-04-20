package com.geo.analytics.domain.inclusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.geo.analytics.domain.gatekeeper.DictionaryGatekeeper;
import com.geo.analytics.domain.metrics.EntropyMetricsCalculator;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine;
import org.junit.jupiter.api.Test;

class InclusionProbabilityCalculatorTest {

    private static final double TAU_BASE = 0.85d;

    private static final double TAU_NUM = 0.13d;

    private static final double TAU_K = 1.5d;

    private static final double TAU_L0 = 3.5d;

    private static final double P_BASE = 0.1d;

    private static final double GAMMA = 0.5d;

    private static final double D_KANA = 0.6d;

    private static final double D_DENOM = 0.4d;

    private static final double P_MIN = 0.0d;

    private static final double P_MAX = 0.25d;

    private final InclusionProbabilityCalculator calculator = new InclusionProbabilityCalculator();

    private static double tauFromEffectiveLength(double lEff) {
        double expArg = StrictMath.fma(TAU_K, StrictMath.fma(-1.0d, TAU_L0, lEff), 0.0d);
        double expVal = StrictMath.exp(expArg);
        double denomSig = StrictMath.fma(1.0d, expVal, 1.0d);
        return StrictMath.fma(TAU_NUM, 1.0d / denomSig, TAU_BASE);
    }

    private static double clampedPFromEntropyDensity(double dEntropy) {
        double numer = StrictMath.fma(-1.0d, D_KANA, dEntropy);
        double ratio = numer / D_DENOM;
        double inner = StrictMath.fma(GAMMA, ratio, 1.0d);
        double pRaw = StrictMath.fma(P_BASE, inner, 0.0d);
        return StrictMath.min(P_MAX, StrictMath.max(P_MIN, pRaw));
    }

    private static double jwMarthaMarhta(double p) {
        double j = 17.0d / 18.0d;
        int l = 3;
        return StrictMath.fma(StrictMath.fma((double) l, p, 0.0d), StrictMath.fma(-1.0d, j, 1.0d), j);
    }

    @Test
    void shouldReturnOneWhenDictionaryHitRegardlessOfOtherSignals() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(true, 0.0, true, "x");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(0.0, 0.0, 0);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure("ignored");
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "y");
        assertThat(pib).isEqualTo(1.0);
    }

    @Test
    void shouldReturnZeroImmediatelyWhenSemanticOutcomeIsFailure() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, "a");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure("timeout");
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "a");
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroImmediatelyWhenSuccessButBrandNotMentioned() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, "a");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(false, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "a");
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldTreatBothNullSidesAsFullJaroMatchAndProduceNonZeroScoreWhenAboveTau() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, null);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, null);
        assertThat(pib).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void shouldTreatBothEmptySidesAsFullJaroMatchAndProduceNonZeroScoreWhenAboveTau() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, "");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "");
        assertThat(pib).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void shouldReturnZeroWhenOneSideEmptyAndOtherHasCharacters() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, "");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "\u30ad");
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroWhenLeftHasCharactersAndRightEmpty() {
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, "\u30ad");
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, "");
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldYieldJaroWinklerOneForIdenticalKatakanaStrings() {
        String k = "\u30ad\u30e4\u30ce\u30f3";
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, k);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, k);
        assertThat(pib).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void shouldReturnZeroForDisjointKatakanaBrandPairWithNoJaroMatches() {
        String left = "\u30ad\u30e4\u30ce\u30f3";
        String right = "\u30bd\u30cb\u30fc";
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, left);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, right);
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldClampPrefixCoefficientLowerBoundToZeroForVerySmallEntropyDensity() {
        String left = "martha";
        String right = "marhta";
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, left);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, -1.0, 6);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, right);
        double p = clampedPFromEntropyDensity(-1.0);
        double expectedJw = jwMarthaMarhta(p);
        assertThat(p).isEqualTo(0.0);
        assertThat(pib).isCloseTo(expectedJw, within(1e-9));
    }

    @Test
    void shouldClampPrefixCoefficientUpperBoundToQuarterForVeryLargeEntropyDensity() {
        String left = "martha";
        String right = "marhta";
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, left);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(3.5, 2.0, 6);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, 1.0));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, right);
        double p = clampedPFromEntropyDensity(2.0);
        double expectedJw = jwMarthaMarhta(p);
        assertThat(p).isEqualTo(0.25);
        assertThat(pib).isCloseTo(expectedJw, within(1e-9));
    }

    @Test
    void shouldReturnZeroWhenFinalScoreEqualsTauOrBelow() {
        String k = "\u30ad\u30e4\u30ce\u30f3";
        double lEff = 3.5;
        double tau = tauFromEffectiveLength(lEff);
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, k);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(lEff, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, tau));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, k);
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroWhenFinalScoreStrictlyBelowTau() {
        String k = "\u30ad\u30e4\u30ce\u30f3";
        double lEff = 3.5;
        double tau = tauFromEffectiveLength(lEff);
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, k);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(lEff, 1.0, 4);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, StrictMath.nextDown(tau)));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, k);
        assertThat(pib).isEqualTo(0.0);
    }

    @Test
    void shouldReturnFinalScoreWhenStrictlyAboveTau() {
        String k = "\u30ad\u30e4\u30ce\u30f3";
        double lEff = 3.5;
        double tau = tauFromEffectiveLength(lEff);
        DictionaryGatekeeper.GatekeeperResult gatekeeper =
                new DictionaryGatekeeper.GatekeeperResult(false, Double.NaN, false, k);
        EntropyMetricsCalculator.EntropyMetrics entropy =
                new EntropyMetricsCalculator.EntropyMetrics(lEff, 1.0, 4);
        double confidence = StrictMath.nextUp(tau);
        SemanticJudgmentEngine.SemanticJudgmentOutcome semantic =
                new SemanticJudgmentEngine.SemanticJudgmentOutcome.Success(
                        new SemanticJudgmentEngine.AiBrandMentionResult(true, confidence));
        double pib = calculator.computePib(gatekeeper, entropy, semantic, k);
        assertThat(pib).isCloseTo(confidence, within(1e-9));
    }
}

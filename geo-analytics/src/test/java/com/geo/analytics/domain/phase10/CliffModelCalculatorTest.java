package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.geo.analytics.domain.phase10.CliffModelCalculator.Bm25Parameters;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class CliffModelCalculatorTest {

    private static final double EPS = 1e-14d;

    @Test
    void persistenceProbabilityRejectsNonPositiveRank() {
        assertThatThrownBy(() -> CliffModelCalculator.persistenceProbability(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CliffModelCalculator.persistenceProbability(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CliffModelCalculator.rankDecayFactor(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CliffModelCalculator.slabScore(0, 0.5d, new Bm25Parameters(1.0d, 100.0d, 100.0d, 1.0d)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistenceProbabilityReturnsSkimBandConstant() {
        assertThat(CliffModelCalculator.persistenceProbability(1)).isEqualTo(0.8d);
        assertThat(CliffModelCalculator.persistenceProbability(3)).isEqualTo(0.8d);
    }

    @Test
    void persistenceProbabilityReturnsExitBandConstant() {
        assertThat(CliffModelCalculator.persistenceProbability(4)).isEqualTo(0.4d);
        assertThat(CliffModelCalculator.persistenceProbability(100)).isEqualTo(0.4d);
    }

    @Test
    void rankDecayMatchesPersistenceRaisedToRankMinusOne() {
        assertThat(CliffModelCalculator.rankDecayFactor(1)).isCloseTo(1.0d, within(EPS));
        assertThat(CliffModelCalculator.rankDecayFactor(3)).isCloseTo(0.64d, within(EPS));
        assertThat(CliffModelCalculator.rankDecayFactor(4)).isCloseTo(0.064d, within(EPS));
    }

    @Test
    void modifiedBm25MatchesHighPrecisionReference() {
        Bm25Parameters params = new Bm25Parameters(2.0d, 120.0d, 100.0d, 1.5d);
        BigDecimal tf = BigDecimal.valueOf(2.0d);
        BigDecimal dl = BigDecimal.valueOf(120.0d);
        BigDecimal avgdl = BigDecimal.valueOf(100.0d);
        BigDecimal idf = BigDecimal.valueOf(1.5d);
        BigDecimal k1 = BigDecimal.valueOf(CliffModelCalculator.K1);
        BigDecimal b = BigDecimal.valueOf(CliffModelCalculator.B);
        BigDecimal dlOverAvgdl = dl.divide(avgdl, new MathContext(80, RoundingMode.HALF_EVEN));
        BigDecimal oneMinusB = BigDecimal.ONE.subtract(b);
        BigDecimal lengthNorm = b.multiply(dlOverAvgdl).add(oneMinusB);
        BigDecimal denom = k1.multiply(lengthNorm).add(tf);
        BigDecimal numer = tf.multiply(k1).add(tf);
        BigDecimal expectedBd = idf.multiply(numer).divide(denom, new MathContext(80, RoundingMode.HALF_EVEN));
        double expected = expectedBd.doubleValue();
        assertThat(CliffModelCalculator.modifiedBm25(params)).isCloseTo(expected, within(EPS));
    }

    @Test
    void slabScoreIsProductOfInclusionRankDecayAndBm25() {
        int rank = 3;
        double pib = 0.625d;
        Bm25Parameters params = new Bm25Parameters(2.0d, 120.0d, 100.0d, 1.5d);
        double decay = CliffModelCalculator.rankDecayFactor(rank);
        double bm25 = CliffModelCalculator.modifiedBm25(params);
        double product = pib * decay * bm25;
        assertThat(CliffModelCalculator.slabScore(rank, pib, params)).isCloseTo(product, within(EPS));
    }
}

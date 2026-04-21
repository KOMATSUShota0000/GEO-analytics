package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BayesianPriorCalculatorTest {

    private static final double K_BASE = 5.0d;
    private static final double LAMBDA = 2.0d;

    @Test
    void priorConstantsMatchHighPrecisionOneThirdAndTwoThirds() {
        assertThat(BayesianPriorCalculator.ALPHA).isEqualTo(1.0d / 3.0d);
        assertThat(BayesianPriorCalculator.BETA).isEqualTo(1.0d / 3.0d);
        assertThat(BayesianPriorCalculator.TOTAL_WEIGHT).isEqualTo(BayesianPriorCalculator.ALPHA + BayesianPriorCalculator.BETA);
        assertThat(BayesianPriorCalculator.TOTAL_WEIGHT).isEqualTo(2.0d / 3.0d);
    }

    @Test
    void adaptiveKAtZeroProgressEqualsKBase() {
        assertThat(BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, 0.0d)).isEqualTo(K_BASE);
    }

    @Test
    void adaptiveKAtFullProgressMatchesExponentialDecay() {
        double expected = K_BASE * StrictMath.exp(-LAMBDA);
        assertThat(BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, 1.0d)).isEqualTo(expected);
    }

    @Test
    void adaptiveKClampsNegativeProgressLikeZero() {
        double atZero = BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, 0.0d);
        assertThat(BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, -0.5d)).isEqualTo(atZero);
    }

    @Test
    void adaptiveKClampsAboveOneProgressLikeOne() {
        double atOne = BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, 1.0d);
        assertThat(BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, 1.5d)).isEqualTo(atOne);
    }

    @Test
    void adaptiveKRejectsNanKBase() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(Double.NaN, LAMBDA, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsNanLambda() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, Double.NaN, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsNanProgress() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsInfiniteKBase() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(Double.POSITIVE_INFINITY, LAMBDA, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(Double.NEGATIVE_INFINITY, LAMBDA, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsInfiniteLambda() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, Double.POSITIVE_INFINITY, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, Double.NEGATIVE_INFINITY, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsInfiniteProgress() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, LAMBDA, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsNonPositiveKBase() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(0.0d, LAMBDA, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(-1.0d, LAMBDA, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptiveKRejectsNegativeLambda() {
        assertThatThrownBy(() -> BayesianPriorCalculator.adaptiveK(K_BASE, -1.0d, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ZibExpectationCalculatorTest {

    @Test
    void expectedShareOfModelMatchesFormulaForTypicalInputs() {
        double sumOfValidScores = 15.0d;
        long nPlanned = 10L;
        double adaptiveK = 2.0d;
        double num = StrictMath.fma(adaptiveK, BayesianPriorCalculator.ALPHA, sumOfValidScores);
        double den = StrictMath.fma(adaptiveK, BayesianPriorCalculator.TOTAL_WEIGHT, (double) nPlanned);
        double expected = num / den;
        assertThat(ZibExpectationCalculator.expectedShareOfModel(sumOfValidScores, nPlanned, adaptiveK))
                .isEqualTo(expected);
    }

    @Test
    void zeroDenominatorReturnsZero() {
        assertThat(ZibExpectationCalculator.expectedShareOfModel(0.0d, 0L, 0.0d)).isEqualTo(0.0d);
    }

    @Test
    void priorOnlyWithUnitKEqualsOneHalf() {
        assertThat(ZibExpectationCalculator.expectedShareOfModel(0.0d, 0L, 1.0d)).isEqualTo(0.5d);
    }

    @Test
    void rejectsNanSumOfValidScores() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(Double.NaN, 10L, 1.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNanAdaptiveK() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(1.0d, 10L, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInfiniteSumOfValidScores() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(Double.POSITIVE_INFINITY, 10L, 1.0d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(Double.NEGATIVE_INFINITY, 10L, 1.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInfiniteAdaptiveK() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(1.0d, 10L, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(1.0d, 10L, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeNPlanned() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(0.0d, -1L, 0.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSumOfValidScores() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(-1.0d, 10L, 1.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAdaptiveK() {
        assertThatThrownBy(() -> ZibExpectationCalculator.expectedShareOfModel(1.0d, 10L, -0.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

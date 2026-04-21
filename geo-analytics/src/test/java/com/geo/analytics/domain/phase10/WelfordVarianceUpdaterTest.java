package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WelfordVarianceUpdaterTest {

    private static final double EPS = 1e-14d;

    private static WelfordVarianceState fold(double... values) {
        WelfordVarianceState state = WelfordVarianceState.empty();
        for (double value : values) {
            state = WelfordVarianceUpdater.update(state, value);
        }
        return state;
    }

    @Test
    void emptyStateReturnsZeroForAllVarianceMetrics() {
        WelfordVarianceState state = WelfordVarianceState.empty();
        assertThat(state.count()).isZero();
        assertThat(state.populationVariance()).isEqualTo(0.0d);
        assertThat(state.sampleVariance()).isEqualTo(0.0d);
        assertThat(state.populationStandardDeviation()).isEqualTo(0.0d);
        assertThat(state.sampleStandardDeviation()).isEqualTo(0.0d);
    }

    @Test
    void singleObservationYieldsZeroSampleSpread() {
        WelfordVarianceState state = WelfordVarianceUpdater.update(WelfordVarianceState.empty(), 7.5d);
        assertThat(state.count()).isEqualTo(1L);
        assertThat(state.sampleVariance()).isEqualTo(0.0d);
        assertThat(state.sampleStandardDeviation()).isEqualTo(0.0d);
    }

    @Test
    void threePointSequenceMatchesClosedFormVariances() {
        WelfordVarianceState state = fold(10.0d, 20.0d, 30.0d);
        assertThat(state.mean()).isCloseTo(20.0d, within(EPS));
        assertThat(state.populationVariance()).isCloseTo(200.0d / 3.0d, within(EPS));
        assertThat(state.sampleVariance()).isCloseTo(100.0d, within(EPS));
        assertThat(state.populationStandardDeviation())
                .isCloseTo(StrictMath.sqrt(200.0d / 3.0d), within(EPS));
        assertThat(state.sampleStandardDeviation()).isCloseTo(10.0d, within(EPS));
    }

    @Test
    void largeBaselineWithTinySpreadPreservesSampleUnitVariance() {
        WelfordVarianceState state = fold(1.0e9d, 1.0e9d + 1.0d, 1.0e9d + 2.0d);
        assertThat(state.mean()).isCloseTo(1.0e9d + 1.0d, within(EPS));
        assertThat(state.sampleVariance()).isCloseTo(1.0d, within(EPS));
    }

    @Test
    void repeatedIdenticalValuesKeepM2AndSpreadAtZero() {
        double value = 3.14159d;
        WelfordVarianceState state = WelfordVarianceState.empty();
        for (int i = 0; i < 10; i++) {
            state = WelfordVarianceUpdater.update(state, value);
        }
        assertThat(state.count()).isEqualTo(10L);
        assertThat(state.mean()).isCloseTo(value, within(EPS));
        assertThat(state.m2()).isEqualTo(0.0d);
        assertThat(state.populationVariance()).isEqualTo(0.0d).isNotNaN();
        assertThat(state.sampleVariance()).isEqualTo(0.0d).isNotNaN();
        assertThat(state.populationStandardDeviation()).isEqualTo(0.0d).isNotNaN();
        assertThat(state.sampleStandardDeviation()).isEqualTo(0.0d).isNotNaN();
        assertThat(state.populationVariance()).isGreaterThanOrEqualTo(0.0d);
        assertThat(state.sampleVariance()).isGreaterThanOrEqualTo(0.0d);
    }
}

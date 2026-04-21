package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PSquareQuantileUpdaterTest {

    private static final double EPS = 1e-14d;

    @Test
    void bootstrapSortsFiveObservationsAndInitializesMarkerPositions() {
        PSquareQuantileState state = PSquareQuantileState.empty();
        state = PSquareQuantileUpdater.update(state, 50.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 10.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 40.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 20.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 30.0d, 1.0d);
        assertThat(state.count()).isEqualTo(5L);
        assertThat(state.q1()).isEqualTo(10.0d);
        assertThat(state.q2()).isEqualTo(20.0d);
        assertThat(state.q3()).isEqualTo(30.0d);
        assertThat(state.q4()).isEqualTo(40.0d);
        assertThat(state.q5()).isEqualTo(50.0d);
        assertThat(state.n1()).isEqualTo(1.0d);
        assertThat(state.n2()).isEqualTo(2.0d);
        assertThat(state.n3()).isEqualTo(3.0d);
        assertThat(state.n4()).isEqualTo(4.0d);
        assertThat(state.n5()).isEqualTo(5.0d);
        assertThat(state.nPrime1()).isEqualTo(1.0d);
        assertThat(state.nPrime2()).isEqualTo(2.0d);
        assertThat(state.nPrime3()).isEqualTo(3.0d);
        assertThat(state.nPrime4()).isEqualTo(4.0d);
        assertThat(state.nPrime5()).isEqualTo(5.0d);
    }

    @Test
    void medianAndIqrReflectCentralSpread() {
        PSquareQuantileState state =
                new PSquareQuantileState(5L, 10.0d, 20.0d, 30.0d, 40.0d, 50.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 1.0d,
                        2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(state.median()).isEqualTo(30.0d);
        assertThat(state.iqr()).isEqualTo(20.0d);
    }

    @Test
    void lambdaScalesMarkersBeforeTargetIncrements() {
        PSquareQuantileState state = PSquareQuantileState.empty();
        state = PSquareQuantileUpdater.update(state, 50.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 10.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 40.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 20.0d, 1.0d);
        state = PSquareQuantileUpdater.update(state, 30.0d, 1.0d);
        double lambda = 0.9d;
        state = PSquareQuantileUpdater.update(state, 5.0d, lambda);
        assertThat(state.count()).isEqualTo(6L);
        assertThat(state.q1()).isEqualTo(5.0d);
        assertThat(state.q2()).isEqualTo(20.0d);
        assertThat(state.q3()).isEqualTo(30.0d);
        assertThat(state.q4()).isEqualTo(40.0d);
        assertThat(state.q5()).isEqualTo(50.0d);
        assertThat(state.n1()).isCloseTo(1.0d * lambda, within(EPS));
        assertThat(state.n2()).isCloseTo(2.0d * lambda + 1.0d, within(EPS));
        assertThat(state.n3()).isCloseTo(3.0d * lambda + 1.0d, within(EPS));
        assertThat(state.n4()).isCloseTo(4.0d * lambda + 1.0d, within(EPS));
        assertThat(state.n5()).isCloseTo(5.0d * lambda + 1.0d, within(EPS));
        assertThat(state.nPrime1()).isCloseTo(1.0d * lambda, within(EPS));
        assertThat(state.nPrime2()).isCloseTo(2.0d * lambda + 0.25d, within(EPS));
        assertThat(state.nPrime3()).isCloseTo(3.0d * lambda + 0.5d, within(EPS));
        assertThat(state.nPrime4()).isCloseTo(4.0d * lambda + 0.75d, within(EPS));
        assertThat(state.nPrime5()).isCloseTo(5.0d * lambda + 1.0d, within(EPS));
    }

    @Test
    void positiveShiftAdjustsQ3AndIncrementsN3WhenDesiredProbabilityExceedsMarker() {
        PSquareQuantileState before =
                new PSquareQuantileState(5L, 10.0d, 20.0d, 30.0d, 40.0d, 50.0d, 1.0d, 2.0d, 3.0d, 5.0d, 6.0d, 1.0d,
                        2.0d, 4.0d, 4.0d, 5.0d);
        double expectedQ3 =
                PSquarePredictor.parabolic(20.0d, 2.0d, 30.0d, 3.0d, 40.0d, 6.0d, 1);
        PSquareQuantileState after = PSquareQuantileUpdater.update(before, 35.0d, 1.0d);
        assertThat(after.q3()).isCloseTo(expectedQ3, within(EPS));
        assertThat(after.n3()).isCloseTo(4.0d, within(EPS));
    }

    @Test
    void negativeShiftAdjustsQ3AndDecrementsN3WhenDesiredProbabilityLagsMarker() {
        PSquareQuantileState before =
                new PSquareQuantileState(5L, 10.0d, 20.0d, 30.0d, 40.0d, 50.0d, 1.0d, 2.0d, 4.0d, 5.0d, 6.0d, 1.0d,
                        2.0d, 2.0d, 4.0d, 5.0d);
        double expectedQ3 =
                PSquarePredictor.parabolic(20.0d, 3.0d, 30.0d, 5.0d, 40.0d, 6.0d, -1);
        PSquareQuantileState after = PSquareQuantileUpdater.update(before, 5.0d, 1.0d);
        assertThat(after.q1()).isEqualTo(5.0d);
        assertThat(after.q3()).isCloseTo(expectedQ3, within(EPS));
        assertThat(after.n3()).isCloseTo(4.0d, within(EPS));
    }
}

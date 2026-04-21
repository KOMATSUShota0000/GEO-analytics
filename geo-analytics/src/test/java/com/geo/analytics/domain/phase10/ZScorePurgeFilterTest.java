package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ZScorePurgeFilterTest {

    @Test
    void nonFiniteSlabScoresArePurgedImmediately() {
        PSquareQuantileState state =
                new PSquareQuantileState(10L, 0.0d, 10.0d, 20.0d, 30.0d, 40.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 1.0d,
                        2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(ZScorePurgeFilter.shouldPurge(state, Double.NaN)).isTrue();
        assertThat(ZScorePurgeFilter.shouldPurge(state, Double.POSITIVE_INFINITY)).isTrue();
        assertThat(ZScorePurgeFilter.shouldPurge(state, Double.NEGATIVE_INFINITY)).isTrue();
    }

    @Test
    void burnInSuppressesPurgeRegardlessOfSlabMagnitude() {
        PSquareQuantileState state =
                new PSquareQuantileState(2L, 0.0d, 10.0d, 20.0d, 30.0d, 40.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 1.0d,
                        2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(ZScorePurgeFilter.shouldPurge(state, 1.0e300d)).isFalse();
    }

    @Test
    void zeroIqrKeepsPurgeInactiveForNearMedianScores() {
        PSquareQuantileState flat =
                new PSquareQuantileState(10L, 10.0d, 10.0d, 10.0d, 10.0d, 10.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 1.0d,
                        2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(ZScorePurgeFilter.shouldPurge(flat, 10.0d)).isFalse();
        assertThat(ZScorePurgeFilter.shouldPurge(flat, 10.0d + 3.4e-10d)).isFalse();
        PSquareQuantileState tinySpread =
                new PSquareQuantileState(10L, 10.0d, 10.0d, 10.0d, 10.000001d, 10.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d,
                        1.0d, 2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(ZScorePurgeFilter.shouldPurge(tinySpread, 10.000001d)).isFalse();
    }

    @Test
    void modifiedZScoreRespectsThreePointFiveThreshold() {
        PSquareQuantileState state =
                new PSquareQuantileState(10L, 0.0d, 93.511d, 100.0d, 107.0d, 200.0d, 1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 1.0d,
                        2.0d, 3.0d, 4.0d, 5.0d);
        assertThat(state.median()).isEqualTo(100.0d);
        assertThat(state.iqr()).isCloseTo(13.489d, within(1e-9d));
        assertThat(ZScorePurgeFilter.shouldPurge(state, 134.0d)).isFalse();
        assertThat(ZScorePurgeFilter.shouldPurge(state, 136.0d)).isTrue();
    }
}

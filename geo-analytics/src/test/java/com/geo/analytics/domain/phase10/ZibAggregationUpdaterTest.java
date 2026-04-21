package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ZibAggregationUpdaterTest {

    @Test
    void emptyStateHasZeroCountAndZeroSum() {
        ZibAggregationState empty = ZibAggregationState.empty();
        assertThat(empty.processedCount()).isZero();
        assertThat(empty.sumOfScores()).isEqualTo(0.0d);
    }

    @Test
    void purgeIgnoresNanSlabScoreAndLeavesStateUnchanged() {
        ZibAggregationState before = new ZibAggregationState(3L, 10.0d);
        ZibAggregationState after =
                ZibAggregationUpdater.update(before, true, false, Double.NaN);
        assertThat(after).isSameAs(before);
        assertThat(after.processedCount()).isEqualTo(3L);
        assertThat(after.sumOfScores()).isEqualTo(10.0d);
    }

    @Test
    void spikeIncrementsCountOnlyAndIgnoresNanSlabScore() {
        ZibAggregationState before = new ZibAggregationState(2L, 5.0d);
        ZibAggregationState after =
                ZibAggregationUpdater.update(before, false, true, Double.NaN);
        assertThat(after.processedCount()).isEqualTo(3L);
        assertThat(after.sumOfScores()).isEqualTo(5.0d);
    }

    @Test
    void slabIncrementsCountAndAddsScore() {
        ZibAggregationState before = new ZibAggregationState(1L, 2.5d);
        ZibAggregationState after =
                ZibAggregationUpdater.update(before, false, false, 3.5d);
        assertThat(after.processedCount()).isEqualTo(2L);
        assertThat(after.sumOfScores()).isEqualTo(6.0d);
    }

    @Test
    void slabRejectsNanSlabScore() {
        ZibAggregationState before = ZibAggregationState.empty();
        assertThatThrownBy(() -> ZibAggregationUpdater.update(before, false, false, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slabRejectsInfiniteSlabScore() {
        ZibAggregationState before = ZibAggregationState.empty();
        assertThatThrownBy(
                        () -> ZibAggregationUpdater.update(before, false, false, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ZibAggregationUpdater.update(before, false, false, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slabRejectsNegativeSlabScore() {
        ZibAggregationState before = ZibAggregationState.empty();
        assertThatThrownBy(() -> ZibAggregationUpdater.update(before, false, false, -1.0d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRejectsNullCurrent() {
        assertThatThrownBy(() -> ZibAggregationUpdater.update(null, false, false, 1.0d))
                .isInstanceOf(NullPointerException.class);
    }
}

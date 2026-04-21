package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScoreCombinerBufferTest {

    @Test
    void constructsWithPositiveCapacity() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(100);
        assertThat(buffer.deltaCount()).isZero();
        assertThat(buffer.deltaSum()).isEqualTo(0.0d);
        assertThat(buffer.isFull()).isFalse();
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new ScoreCombinerBuffer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreCombinerBuffer(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purgeDoesNotChangeInternalState() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, false, 2.0d);
        buffer.add(false, true, 0.0d);
        long countBefore = buffer.deltaCount();
        double sumBefore = buffer.deltaSum();
        buffer.add(true, false, Double.NaN);
        assertThat(buffer.deltaCount()).isEqualTo(countBefore);
        assertThat(buffer.deltaSum()).isEqualTo(sumBefore);
    }

    @Test
    void spikeIncrementsCountsOnlyAndLeavesDeltaSumUnchangedFromZero() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, true, 0.0d);
        assertThat(buffer.deltaCount()).isEqualTo(1L);
        assertThat(buffer.deltaSum()).isEqualTo(0.0d);
    }

    @Test
    void slabIncrementsCountsAndAccumulatesSum() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, false, 1.5d);
        buffer.add(false, false, 2.5d);
        assertThat(buffer.deltaCount()).isEqualTo(2L);
        assertThat(buffer.deltaSum()).isEqualTo(4.0d);
    }

    @Test
    void slabRejectsNanWithoutMutatingState() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, false, 3.0d);
        long dc = buffer.deltaCount();
        double ds = buffer.deltaSum();
        assertThatThrownBy(() -> buffer.add(false, false, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.deltaCount()).isEqualTo(dc);
        assertThat(buffer.deltaSum()).isEqualTo(ds);
    }

    @Test
    void slabRejectsInfinityWithoutMutatingState() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, false, 1.0d);
        long dc = buffer.deltaCount();
        double ds = buffer.deltaSum();
        assertThatThrownBy(() -> buffer.add(false, false, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.deltaCount()).isEqualTo(dc);
        assertThat(buffer.deltaSum()).isEqualTo(ds);
        assertThatThrownBy(() -> buffer.add(false, false, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.deltaCount()).isEqualTo(dc);
        assertThat(buffer.deltaSum()).isEqualTo(ds);
    }

    @Test
    void slabRejectsNegativeWithoutMutatingState() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, true, 0.0d);
        long dc = buffer.deltaCount();
        double ds = buffer.deltaSum();
        assertThatThrownBy(() -> buffer.add(false, false, -1.0d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.deltaCount()).isEqualTo(dc);
        assertThat(buffer.deltaSum()).isEqualTo(ds);
    }

    @Test
    void isFullWhenCurrentSizeReachesCapacityAndResetClearsAll() {
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(3);
        assertThat(buffer.isFull()).isFalse();
        buffer.add(false, false, 1.0d);
        assertThat(buffer.isFull()).isFalse();
        buffer.add(false, true, 0.0d);
        assertThat(buffer.isFull()).isFalse();
        buffer.add(false, false, 2.0d);
        assertThat(buffer.deltaCount()).isEqualTo(3L);
        assertThat(buffer.isFull()).isTrue();
        buffer.reset();
        assertThat(buffer.deltaCount()).isZero();
        assertThat(buffer.deltaSum()).isEqualTo(0.0d);
        assertThat(buffer.isFull()).isFalse();
    }
}

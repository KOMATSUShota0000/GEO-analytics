package com.geo.analytics.domain.phase10;

import java.util.Objects;

public final class ZibAggregationUpdater {

    private ZibAggregationUpdater() {
    }

    public static ZibAggregationState update(
            ZibAggregationState current, boolean isPurged, boolean isSpike, double slabScore) {
        Objects.requireNonNull(current);
        if (isPurged) {
            return current;
        }
        if (isSpike) {
            return new ZibAggregationState(current.processedCount() + 1L, current.sumOfScores());
        }
        if (!Double.isFinite(slabScore) || slabScore < 0.0d) {
            throw new IllegalArgumentException();
        }
        return new ZibAggregationState(
                current.processedCount() + 1L, current.sumOfScores() + slabScore);
    }
}

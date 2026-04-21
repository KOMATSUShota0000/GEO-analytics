package com.geo.analytics.domain.phase10;

public record ZibAggregationState(long processedCount, double sumOfScores) {

    public static ZibAggregationState empty() {
        return new ZibAggregationState(0L, 0.0d);
    }
}

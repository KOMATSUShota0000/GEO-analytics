package com.geo.analytics.domain.phase10;

import java.util.concurrent.atomic.AtomicReference;

public final class ZibAggregationOrchestrator {

    private final AtomicReference<ZibAggregationState> stateRef;

    public ZibAggregationOrchestrator() {
        stateRef = new AtomicReference<>(ZibAggregationState.empty());
    }

    public void flush(ScoreCombinerBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException();
        }
        long dCount = buffer.deltaCount();
        if (dCount == 0L) {
            return;
        }
        double dSum = buffer.deltaSum();
        while (true) {
            ZibAggregationState current = stateRef.get();
            ZibAggregationState next =
                    new ZibAggregationState(current.processedCount() + dCount, current.sumOfScores() + dSum);
            if (stateRef.compareAndSet(current, next)) {
                buffer.reset();
                return;
            }
            Thread.onSpinWait();
        }
    }

    public ZibAggregationState currentState() {
        return stateRef.get();
    }
}

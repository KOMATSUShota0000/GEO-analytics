package com.geo.analytics.domain.phase10;

import java.util.concurrent.locks.ReentrantLock;

public final class GlobalScorePipeline implements StreamingScorePipeline {

    private final StatisticalEngineOrchestrator statisticalEngine;
    private final ZibAggregationOrchestrator zibOrchestrator;
    private final ScoreCombinerBuffer combinerBuffer;
    private final ScorePublisher publisher;
    private final ReentrantLock bufferLock;
    private final double kBase;
    private final double lambda;
    private final long nPlanned;
    private final double progress;

    public GlobalScorePipeline(
            StatisticalEngineOrchestrator statisticalEngine,
            ZibAggregationOrchestrator zibOrchestrator,
            ScoreCombinerBuffer combinerBuffer,
            ScorePublisher publisher,
            double kBase,
            double lambda,
            long nPlanned,
            double progress) {
        this.statisticalEngine = statisticalEngine;
        this.zibOrchestrator = zibOrchestrator;
        this.combinerBuffer = combinerBuffer;
        this.publisher = publisher;
        this.bufferLock = new ReentrantLock();
        this.kBase = kBase;
        this.lambda = lambda;
        this.nPlanned = nPlanned;
        this.progress = progress;
    }

    @Override
    public void acceptScore(String brandId, boolean isSpike, double slabScore) {
        boolean isPurged;
        if (isSpike) {
            isPurged = false;
        } else {
            isPurged = statisticalEngine.updateAndCheck(slabScore, lambda).purged();
        }
        bufferLock.lock();
        try {
            combinerBuffer.add(isPurged, isSpike, slabScore);
            publisher.publishObservation(brandId, isSpike, slabScore, isPurged);
            if (combinerBuffer.isFull()) {
                zibOrchestrator.flush(combinerBuffer);
                ZibAggregationState state = zibOrchestrator.currentState();
                double adaptiveK = BayesianPriorCalculator.adaptiveK(kBase, lambda, progress);
                double expectedSom =
                        ZibExpectationCalculator.expectedShareOfModel(state.sumOfScores(), nPlanned, adaptiveK);
                publisher.publishSnapshot(
                        brandId, expectedSom, state.processedCount(), nPlanned, progress);
            }
        } finally {
            bufferLock.unlock();
        }
    }
}

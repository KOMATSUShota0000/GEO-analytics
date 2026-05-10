package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalScorePipelineThroughputTest {

    private static final class CountingPublisher implements ScorePublisher {

        @Override
        public void publishSnapshot(
                String brandId,
                double expectedShareOfModel,
                long processedCount,
                long nPlanned,
                double progress) {}

        @Override
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {}
    }

    @Test
    void millionEventsUnderThunderingHerdRetainExactProcessedCount() {
        int bufferCapacity = 100;
        double kBase = 5.0d;
        double lambda = 2.0d;
        long nPlanned = 1_000_000L;
        double progress = 0.5d;
        CountingPublisher publisher = new CountingPublisher();
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(bufferCapacity);
        GlobalScorePipeline pipeline =
                new GlobalScorePipeline(
                        statisticalEngine,
                        zibOrchestrator,
                        buffer,
                        publisher,
                        kBase,
                        lambda,
                        nPlanned,
                        progress);
        String brandId = "throughput-brand";
        double slabScore = 2.0d;
        long t0 = System.nanoTime();
        for (long e = 0; e < 1_000_000L; e++) {
            pipeline.acceptScore(brandId, false, slabScore);
        }
        long t1 = System.nanoTime();
        double elapsedMs = (t1 - t0) / 1_000_000.0d;
        double elapsedSeconds = (t1 - t0) / 1_000_000_000.0d;
        double ops = 1_000_000L / elapsedSeconds;
        System.out.println("elapsedMs=" + elapsedMs);
        System.out.println("ops=" + ops);
        assertThat(zibOrchestrator.currentState().processedCount()).isEqualTo(1_000_000L);
    }
}

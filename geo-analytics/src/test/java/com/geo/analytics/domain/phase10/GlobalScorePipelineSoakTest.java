package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GlobalScorePipelineSoakTest {

    private static final class CountingPublisher implements ScorePublisher {

        private final AtomicInteger snapshotCount = new AtomicInteger();
        private final AtomicInteger observationCount = new AtomicInteger();

        @Override
        public void publishSnapshot(
                String brandId,
                double expectedShareOfModel,
                long processedCount,
                long nPlanned,
                double progress) {
            snapshotCount.incrementAndGet();
        }

        @Override
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {
            observationCount.incrementAndGet();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fiftyMillionEventsRetainExactAggregation() {
        long totalEvents = 50_000_000L;
        int bufferCapacity = 1_000;
        double kBase = 5.0d;
        double lambda = 2.0d;
        long nPlanned = 50_000_000L;
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
        String brandId = "soak-brand";
        for (long i = 0L; i < totalEvents; i++) {
            if (i % 5L == 0L) {
                pipeline.acceptScore(brandId, true, 0.0d);
            } else {
                pipeline.acceptScore(brandId, false, 1.0d);
            }
        }
        ZibAggregationState state = zibOrchestrator.currentState();
        assertThat(state.processedCount()).isEqualTo(50_000_000L);
        assertThat(state.sumOfScores()).isEqualTo(40_000_000.0d);
    }
}

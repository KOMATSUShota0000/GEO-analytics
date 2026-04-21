package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class GlobalScorePipelineColdStartTest {

    private static final class LastSnapshotPublisher implements ScorePublisher {

        private volatile double lastExpectedShareOfModel;
        private volatile long lastProcessedCount;

        @Override
        public void publishSnapshot(
                String brandId,
                double expectedShareOfModel,
                long processedCount,
                long nPlanned,
                double progress) {
            this.lastExpectedShareOfModel = expectedShareOfModel;
            this.lastProcessedCount = processedCount;
        }

        @Override
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {}
    }

    @Test
    void coldStartWithPoisonPillRecoversToStableShareOfModel() {
        LastSnapshotPublisher publisher = new LastSnapshotPublisher();
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(5);
        double kBase = 5.0d;
        double lambda = 2.0d;
        long nPlanned = 1000L;
        double progress = 0.1d;
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
        String brandId = "cold-brand";
        assertThatCode(
                        () -> {
                            pipeline.acceptScore(brandId, false, 1.0d);
                            pipeline.acceptScore(brandId, false, 1.2d);
                            pipeline.acceptScore(brandId, false, 0.8d);
                            pipeline.acceptScore(brandId, false, 10000.0d);
                            pipeline.acceptScore(brandId, true, 0.0d);
                            for (int i = 0; i < 95; i++) {
                                pipeline.acceptScore(brandId, false, 1.0d);
                            }
                        })
                .doesNotThrowAnyException();
        ZibAggregationState state = zibOrchestrator.currentState();
        assertThat(state.processedCount()).isEqualTo(95L);
        assertThat(state.sumOfScores()).isEqualTo(94.0d);
        assertThat(publisher.lastExpectedShareOfModel).isFinite();
        assertThat(Double.isNaN(publisher.lastExpectedShareOfModel)).isFalse();
        assertThat(publisher.lastExpectedShareOfModel).isGreaterThanOrEqualTo(0.0d);
        assertThat(publisher.lastExpectedShareOfModel).isLessThanOrEqualTo(2.0d);
        assertThat(publisher.lastProcessedCount).isEqualTo(95L);
    }
}

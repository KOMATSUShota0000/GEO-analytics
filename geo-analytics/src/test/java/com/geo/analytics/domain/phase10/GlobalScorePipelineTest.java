package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalScorePipelineTest {

    private static final class RecordingPublisher implements ScorePublisher {

        private final List<SnapshotRecord> snapshots = new ArrayList<>();
        private final List<ObservationRecord> observations = new ArrayList<>();

        @Override
        public void publishSnapshot(
                String brandId,
                double expectedShareOfModel,
                long processedCount,
                long nPlanned,
                double progress) {
            snapshots.add(
                    new SnapshotRecord(brandId, expectedShareOfModel, processedCount, nPlanned, progress));
        }

        @Override
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {
            observations.add(new ObservationRecord(brandId, isSpike, slabScore, purged));
        }
    }

    private record SnapshotRecord(
            String brandId,
            double expectedShareOfModel,
            long processedCount,
            long nPlanned,
            double progress) {}

    private record ObservationRecord(String brandId, boolean isSpike, double slabScore, boolean purged) {}

    @Test
    void integrationComputesExpectedSomOnBufferFlush() {
        RecordingPublisher publisher = new RecordingPublisher();
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(3);
        double kBase = 2.0d;
        double lambda = 1.0d;
        long nPlanned = 10L;
        double progress = 0.25d;
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
        pipeline.acceptScore("acme", false, 0.5d);
        pipeline.acceptScore("acme", true, 0.0d);
        pipeline.acceptScore("acme", false, 0.5d);
        assertThat(publisher.snapshots).hasSize(1);
        ZibAggregationState state = zibOrchestrator.currentState();
        double adaptiveK = BayesianPriorCalculator.adaptiveK(kBase, lambda, progress);
        double expectedSom =
                ZibExpectationCalculator.expectedShareOfModel(state.sumOfScores(), nPlanned, adaptiveK);
        SnapshotRecord snap = publisher.snapshots.get(0);
        assertThat(snap.brandId()).isEqualTo("acme");
        assertThat(snap.expectedShareOfModel()).isEqualTo(expectedSom);
        assertThat(snap.processedCount()).isEqualTo(state.processedCount());
        assertThat(snap.nPlanned()).isEqualTo(nPlanned);
        assertThat(snap.progress()).isEqualTo(progress);
        assertThat(state.processedCount()).isEqualTo(3L);
        assertThat(state.sumOfScores()).isEqualTo(1.0d);
    }

    @Test
    void purgedSlabDoesNotIncrementZibProcessedCount() {
        RecordingPublisher publisher = new RecordingPublisher();
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(4);
        GlobalScorePipeline pipeline =
                new GlobalScorePipeline(
                        statisticalEngine,
                        zibOrchestrator,
                        buffer,
                        publisher,
                        2.0d,
                        1.0d,
                        100L,
                        0.0d);
        pipeline.acceptScore("x", false, 0.1d);
        pipeline.acceptScore("x", false, 0.2d);
        assertThat(zibOrchestrator.currentState().processedCount()).isZero();
        pipeline.acceptScore("x", false, Double.NaN);
        assertThat(zibOrchestrator.currentState().processedCount()).isZero();
        pipeline.acceptScore("x", false, 0.3d);
        pipeline.acceptScore("x", false, 0.4d);
        assertThat(zibOrchestrator.currentState().processedCount()).isEqualTo(4L);
        assertThat(publisher.snapshots).hasSize(1);
    }
}

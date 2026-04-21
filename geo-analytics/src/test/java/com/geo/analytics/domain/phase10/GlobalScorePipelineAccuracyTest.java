package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalScorePipelineAccuracyTest {

    private static final class RecordingPublisher implements ScorePublisher {

        private final List<SnapshotRecord> snapshots = new ArrayList<>();

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
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {}
    }

    private record SnapshotRecord(
            String brandId,
            double expectedShareOfModel,
            long processedCount,
            long nPlanned,
            double progress) {}

    @Test
    void goldenMasterMatchesExpectedZibStateAndShareOfModel() {
        RecordingPublisher publisher = new RecordingPublisher();
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(50);
        double kBase = 5.0d;
        double lambda = 2.0d;
        long nPlanned = 1000L;
        double progress = 0.9d;
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
        String brandId = "golden-brand";
        for (int i = 0; i < 800; i++) {
            pipeline.acceptScore(brandId, false, 2.0d);
        }
        for (int i = 0; i < 100; i++) {
            pipeline.acceptScore(brandId, true, 0.0d);
        }
        for (int i = 0; i < 100; i++) {
            pipeline.acceptScore(brandId, false, 10000.0d);
        }
        assertThat(publisher.snapshots).isNotEmpty();
        SnapshotRecord last = publisher.snapshots.get(publisher.snapshots.size() - 1);
        double adaptiveK = 5.0d * StrictMath.exp(-2.0d * 0.9d);
        double num = StrictMath.fma(adaptiveK, 1.0d / 3.0d, 1600.0d);
        double den = StrictMath.fma(adaptiveK, 2.0d / 3.0d, 1000.0d);
        double expectedSom = num / den;
        assertThat(last.processedCount()).isEqualTo(900L);
        assertThat(last.expectedShareOfModel()).isCloseTo(expectedSom, within(1e-12d));
    }
}

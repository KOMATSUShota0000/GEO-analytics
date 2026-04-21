package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GlobalScorePipelineThroughputTest {

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
    void millionEventsUnderThunderingHerdRetainExactProcessedCount() throws Exception {
        int taskCount = 10_000;
        int eventsPerTask = 100;
        long totalEvents = 1_000_000L;
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
        CountDownLatch started = new CountDownLatch(taskCount);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < taskCount; t++) {
                executor.submit(
                        () -> {
                            started.countDown();
                            try {
                                release.await();
                                for (int e = 0; e < eventsPerTask; e++) {
                                    pipeline.acceptScore(brandId, false, slabScore);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                done.countDown();
                            }
                        });
            }
            assertThat(started.await(10L, TimeUnit.MINUTES)).isTrue();
            long t0 = System.nanoTime();
            release.countDown();
            assertThat(done.await(30L, TimeUnit.MINUTES)).isTrue();
            long t1 = System.nanoTime();
            double elapsedSeconds = (t1 - t0) / 1_000_000_000.0d;
            double elapsedMs = (t1 - t0) / 1_000_000.0d;
            double ops = totalEvents / elapsedSeconds;
            System.out.println("elapsedMs=" + elapsedMs);
            System.out.println("ops=" + ops);
        }
        assertThat(zibOrchestrator.currentState().processedCount()).isEqualTo(1_000_000L);
    }
}

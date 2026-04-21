package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class GlobalScorePipelineResilienceTest {

    private static final class ChaosPublisher implements ScorePublisher {

        private final double exceptionProbability;
        private final long delayMs;
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger delayCount = new AtomicInteger();

        private ChaosPublisher(double exceptionProbability, long delayMs) {
            this.exceptionProbability = exceptionProbability;
            this.delayMs = delayMs;
        }

        private void beforePublish() {
            if (delayMs > 0L) {
                delayCount.incrementAndGet();
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            if (exceptionProbability > 0.0d
                    && ThreadLocalRandom.current().nextDouble() < exceptionProbability) {
                failureCount.incrementAndGet();
                throw new RuntimeException("chaos");
            }
            successCount.incrementAndGet();
        }

        @Override
        public void publishSnapshot(
                String brandId,
                double expectedShareOfModel,
                long processedCount,
                long nPlanned,
                double progress) {
            beforePublish();
        }

        @Override
        public void publishObservation(String brandId, boolean isSpike, double slabScore, boolean purged) {
            beforePublish();
        }
    }

    @Test
    void publisherExceptionsDoNotCrashCallerAndLockIsReleased() {
        ChaosPublisher chaosPublisher = new ChaosPublisher(1.0d, 0L);
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(100);
        GlobalScorePipeline pipeline =
                new GlobalScorePipeline(
                        statisticalEngine,
                        zibOrchestrator,
                        buffer,
                        chaosPublisher,
                        5.0d,
                        2.0d,
                        1000L,
                        0.5d);
        String brandId = "chaos-a";
        int iterations = 10;
        assertThatCode(
                        () -> {
                            int caught = 0;
                            for (int i = 0; i < iterations; i++) {
                                try {
                                    pipeline.acceptScore(brandId, false, 2.0d);
                                } catch (RuntimeException e) {
                                    caught++;
                                }
                            }
                            assertThat(caught).isEqualTo(iterations);
                        })
                .doesNotThrowAnyException();
        assertThat(chaosPublisher.failureCount.get()).isEqualTo(iterations);
        StatisticalEngineOrchestrator cleanEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator cleanZib = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer cleanBuffer = new ScoreCombinerBuffer(1);
        ChaosPublisher cleanPublisher = new ChaosPublisher(0.0d, 0L);
        GlobalScorePipeline cleanPipeline =
                new GlobalScorePipeline(
                        cleanEngine,
                        cleanZib,
                        cleanBuffer,
                        cleanPublisher,
                        5.0d,
                        2.0d,
                        10L,
                        0.5d);
        cleanPipeline.acceptScore(brandId, false, 2.0d);
        assertThat(cleanZib.currentState().processedCount()).isEqualTo(1L);
    }

    @Test
    void highPublisherLatencyConcurrentSubmissionsPreserveProcessedCount() throws Exception {
        ChaosPublisher chaosPublisher = new ChaosPublisher(0.0d, 50L);
        StatisticalEngineOrchestrator statisticalEngine = new StatisticalEngineOrchestrator();
        ZibAggregationOrchestrator zibOrchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(100);
        GlobalScorePipeline pipeline =
                new GlobalScorePipeline(
                        statisticalEngine,
                        zibOrchestrator,
                        buffer,
                        chaosPublisher,
                        5.0d,
                        2.0d,
                        1000L,
                        0.5d);
        String brandId = "chaos-b";
        int taskCount = 100;
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
                                pipeline.acceptScore(brandId, false, 2.0d);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                done.countDown();
                            }
                        });
            }
            assertThat(started.await(5L, TimeUnit.MINUTES)).isTrue();
            release.countDown();
            assertThat(done.await(15L, TimeUnit.MINUTES)).isTrue();
        }
        assertThat(zibOrchestrator.currentState().processedCount()).isEqualTo(100L);
        assertThat(chaosPublisher.failureCount.get()).isZero();
    }
}

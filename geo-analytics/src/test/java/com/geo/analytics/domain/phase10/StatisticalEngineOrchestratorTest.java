package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StatisticalEngineOrchestratorTest {

    private static final CliffModelCalculator.Bm25Parameters PARAMS =
            new CliffModelCalculator.Bm25Parameters(1.0d, 100.0d, 100.0d, 1.0d);

    @Test
    void processAdvancesWelfordCountFromZeroToOne() {
        StatisticalEngineOrchestrator orchestrator = new StatisticalEngineOrchestrator();
        assertThat(orchestrator.state().welford().count()).isZero();
        OrchestrationResult result = orchestrator.process(1, 0.5d, PARAMS, 1.0d);
        assertThat(result.purged()).isFalse();
        assertThat(orchestrator.state().welford().count()).isEqualTo(1L);
    }

    @Test
    void nanSlabPurgesWithoutWelfordIncrement() {
        StatisticalEngineOrchestrator orchestrator = new StatisticalEngineOrchestrator();
        assertThat(orchestrator.state().welford().count()).isZero();
        OrchestrationResult result = orchestrator.process(1, Double.NaN, PARAMS, 1.0d);
        assertThat(result.purged()).isTrue();
        assertThat(orchestrator.state().welford().count()).isZero();
    }

    @Test
    void thousandVirtualThreadsReachExactWelfordCountWithoutLostUpdates() throws Exception {
        StatisticalEngineOrchestrator orchestrator = new StatisticalEngineOrchestrator();
        int threads = 1000;
        CountDownLatch registered = new CountDownLatch(threads);
        CountDownLatch begin = new CountDownLatch(1);
        List<Future<OrchestrationResult>> futures = new ArrayList<>(threads);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    registered.countDown();
                    begin.await();
                    return orchestrator.process(1, 0.5d, PARAMS, 1.0d);
                }));
            }
            assertThat(registered.await(120L, TimeUnit.SECONDS)).isTrue();
            begin.countDown();
            for (Future<OrchestrationResult> future : futures) {
                OrchestrationResult r = future.get(180L, TimeUnit.SECONDS);
                assertThat(r.purged()).isFalse();
                assertThat(Double.isFinite(r.slabScore())).isTrue();
            }
        }
        assertThat(orchestrator.state().welford().count()).isEqualTo(1000L);
    }

    @Test
    void spinContentionStillDeliversFiniteResultsForEveryVirtualThread() throws Exception {
        StatisticalEngineOrchestrator orchestrator = new StatisticalEngineOrchestrator();
        int threads = 1000;
        CountDownLatch registered = new CountDownLatch(threads);
        CountDownLatch begin = new CountDownLatch(1);
        List<Future<OrchestrationResult>> futures = new ArrayList<>(threads);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    registered.countDown();
                    begin.await();
                    return orchestrator.process(2, 0.625d, PARAMS, 1.0d);
                }));
            }
            assertThat(registered.await(120L, TimeUnit.SECONDS)).isTrue();
            begin.countDown();
            for (Future<OrchestrationResult> future : futures) {
                OrchestrationResult r = future.get(180L, TimeUnit.SECONDS);
                assertThat(r).isNotNull();
                assertThat(Double.isFinite(r.slabScore())).isTrue();
            }
        }
    }
}

package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ZibAggregationOrchestratorTest {

    @Test
    void initialStateIsEmpty() {
        ZibAggregationOrchestrator orchestrator = new ZibAggregationOrchestrator();
        assertThat(orchestrator.currentState()).isEqualTo(ZibAggregationState.empty());
    }

    @Test
    void flushUpdatesGlobalStateAndResetsBuffer() {
        ZibAggregationOrchestrator orchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        buffer.add(false, false, 2.0d);
        buffer.add(false, true, 0.0d);
        buffer.add(false, false, 3.0d);
        orchestrator.flush(buffer);
        ZibAggregationState state = orchestrator.currentState();
        assertThat(state.processedCount()).isEqualTo(3L);
        assertThat(state.sumOfScores()).isEqualTo(5.0d);
        assertThat(buffer.deltaCount()).isZero();
        assertThat(buffer.deltaSum()).isEqualTo(0.0d);
    }

    @Test
    void flushWithZeroDeltaLeavesGlobalStateUnchanged() {
        ZibAggregationOrchestrator orchestrator = new ZibAggregationOrchestrator();
        ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(10);
        ZibAggregationState before = orchestrator.currentState();
        orchestrator.flush(buffer);
        assertThat(orchestrator.currentState()).isEqualTo(before);
    }

    @Test
    void concurrentFlushesRetainAllUpdates() throws Exception {
        ZibAggregationOrchestrator orchestrator = new ZibAggregationOrchestrator();
        int taskCount = 1000;
        int slabsPerTask = 10;
        double slabScore = 1.0d;
        CountDownLatch started = new CountDownLatch(taskCount);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(
                        () -> {
                            started.countDown();
                            try {
                                release.await();
                                ScoreCombinerBuffer buffer = new ScoreCombinerBuffer(slabsPerTask);
                                for (int j = 0; j < slabsPerTask; j++) {
                                    buffer.add(false, false, slabScore);
                                }
                                orchestrator.flush(buffer);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                done.countDown();
                            }
                        });
            }
            started.await();
            release.countDown();
            done.await();
        }
        ZibAggregationState state = orchestrator.currentState();
        assertThat(state.processedCount()).isEqualTo((long) taskCount * slabsPerTask);
        assertThat(state.sumOfScores()).isEqualTo((double) taskCount * slabsPerTask * slabScore);
    }
}

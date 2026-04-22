package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NativeStatsUpdaterTest {

  @Test
  void manyVirtualThreadsObserveWelfordConcurrently() throws Exception {
    int threadCount = 400;
    int iterationsPerThread = 2500;
    long expectedCount = (long) threadCount * (long) iterationsPerThread;
    try (NativeStatsManager manager = new NativeStatsManager(1)) {
      NativeStatsUpdater updater = new NativeStatsUpdater(manager.handle(0));
      List<Future<?>> futures = new ArrayList<>(threadCount);
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int t = 0; t < threadCount; t++) {
          Future<?> future =
              executor.submit(
                  () -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                      updater.observeWelford(1.0d);
                    }
                    return null;
                  });
          futures.add(future);
        }
        for (Future<?> future : futures) {
          future.get(10L, TimeUnit.MINUTES);
        }
      }
      double[] buf = new double[3];
      int attempts = 0;
      while (!updater.tryReadWelford(buf)) {
        Thread.onSpinWait();
        attempts++;
        assertThat(attempts).isLessThan(10_000_000);
      }
      assertThat(buf[0]).isEqualTo((double) expectedCount);
      assertThat(buf[1]).isCloseTo(1.0d, within(1e-6d));
      assertThat(buf[2]).isCloseTo(0.0d, within(1e-6d));
    }
  }

  @Test
  void concurrentMixedValuesMatchPopulationStatistics() throws Exception {
    int threadCount = 200;
    int iterationsPerThread = 5000;
    long total = (long) threadCount * (long) iterationsPerThread;
    try (NativeStatsManager manager = new NativeStatsManager(1)) {
      NativeStatsUpdater updater = new NativeStatsUpdater(manager.handle(0));
      List<Future<?>> futures = new ArrayList<>(threadCount);
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int t = 0; t < threadCount; t++) {
          final int tid = t;
          Future<?> future =
              executor.submit(
                  () -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                      double x = (double) (tid + i) * 0.25d;
                      updater.observeWelford(x);
                    }
                    return null;
                  });
          futures.add(future);
        }
        for (Future<?> future : futures) {
          future.get(10L, TimeUnit.MINUTES);
        }
      }
      double sum = 0.0d;
      for (int t = 0; t < threadCount; t++) {
        for (int i = 0; i < iterationsPerThread; i++) {
          sum += (double) (t + i) * 0.25d;
        }
      }
      double expectedMean = sum / (double) total;
      double expectedM2 = 0.0d;
      for (int t = 0; t < threadCount; t++) {
        for (int i = 0; i < iterationsPerThread; i++) {
          double x = (double) (t + i) * 0.25d;
          double d = x - expectedMean;
          expectedM2 += d * d;
        }
      }
      double[] buf = new double[3];
      int attempts = 0;
      while (!updater.tryReadWelford(buf)) {
        Thread.onSpinWait();
        attempts++;
        assertThat(attempts).isLessThan(10_000_000);
      }
      assertThat(buf[0]).isEqualTo((double) total);
      assertThat(buf[1]).isCloseTo(expectedMean, within(1e-3d));
      assertThat(buf[2]).isCloseTo(expectedM2, within(10.0d));
    }
  }
}

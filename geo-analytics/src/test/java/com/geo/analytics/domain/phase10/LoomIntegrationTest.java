package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * TenantScope（ScopedValue）、ParallelTaskOrchestrator（構造化並行性）、NativeStatsUpdater /
 * DictionaryRegistry の Loom 統合を検証する。
 */
class LoomIntegrationTest {

  private static final String HI_KEY = "hi";

  @Test
  @Timeout(value = 12, unit = TimeUnit.MINUTES)
  void scopedValueBindingSurvivesDeepVirtualForkHierarchy() throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> topLevel = new ArrayList<>(500);
      for (int tenantIndex = 0; tenantIndex < 500; tenantIndex++) {
        final int expected = tenantIndex;
        topLevel.add(
            executor.submit(
                () ->
                    TenantScope.execute(
                        new TenantContext(expected, false),
                        () -> {
                          List<Integer> row =
                              ParallelTaskOrchestrator.invokeAllOrdered(
                                  List.of(
                                      () -> verifyTenantIndex(expected),
                                      () -> verifyTenantIndex(expected),
                                      () -> verifyTenantIndex(expected)));
                          assertThat(row).containsExactly(expected, expected, expected);
                        })));
      }
      for (Future<?> f : topLevel) {
        f.get(7L, TimeUnit.MINUTES);
      }
    }
  }

  private static int verifyTenantIndex(int expected) {
    assertThat(TenantScope.currentTenantIndex()).isEqualTo(expected);
    return expected;
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void seqLockPreventsLostUpdatesUnderHighVirtualThreadContention() throws Exception {
    try (NativeStatsManager manager = new NativeStatsManager(1)) {
      NativeStatsUpdater updater = new NativeStatsUpdater(manager.handle(0));
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<?>> futures = new ArrayList<>(2000);
        for (int t = 0; t < 2000; t++) {
          futures.add(
              executor.submit(
                  () -> {
                    for (int i = 0; i < 10; i++) {
                      updater.observeWelford(1.0d);
                    }
                  }));
        }
        for (Future<?> f : futures) {
          f.get(12L, TimeUnit.MINUTES);
        }
      }
      double[] buf = new double[3];
      int spins = 0;
      while (!updater.tryReadWelford(buf)) {
        Thread.onSpinWait();
        spins++;
        assertThat(spins).isLessThan(50_000_000);
      }
      assertThat(buf[0]).isEqualTo(20_000.0d);
    }
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void structuredFailureInterruptsLongRunningPeerVirtualThreads() throws Exception {
    CountDownLatch sawInterrupt = new CountDownLatch(10);
    List<Callable<String>> tasks = new ArrayList<>(11);
    tasks.add(
        () -> {
          throw new IllegalStateException("immediate-failure");
        });
    for (int i = 0; i < 10; i++) {
      tasks.add(
          () -> {
            try {
              Thread.sleep(TimeUnit.HOURS.toMillis(1L));
            } catch (InterruptedException e) {
              sawInterrupt.countDown();
              Thread.currentThread().interrupt();
              throw e;
            }
            return "never";
          });
    }
    long t0 = System.nanoTime();
    assertThatThrownBy(() -> ParallelTaskOrchestrator.invokeAllOrdered(tasks))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("immediate-failure");
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    assertThat(elapsedMs).isLessThan(60_000L);
    assertThat(sawInterrupt.await(45L, TimeUnit.SECONDS))
        .as("all 10 slow peers must observe interrupt/cancel")
        .isTrue();
  }

  @Test
  @Timeout(value = 6, unit = TimeUnit.MINUTES)
  void dictionaryHotReloadDoesNotTearReadersAndSwitchesSearchSemantics(@TempDir Path tempDir)
      throws Exception {
    Path dictV1 = writeSampleTrieDict(tempDir.resolve("dict-v1.bin"), 1);
    Path dictV2 = writeSampleTrieDict(tempDir.resolve("dict-v2.bin"), 42);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
    AtomicInteger preReloadSearches = new AtomicInteger();
    AtomicBoolean reloadFinished = new AtomicBoolean();

    try (DictionaryRegistry registry = new DictionaryRegistry(dictV1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> reloader =
          executor.submit(
              () -> {
                try {
                  Thread.sleep(50L);
                  while (preReloadSearches.get() < 20_000) {
                    Thread.onSpinWait();
                  }
                  registry.reload(dictV2);
                  reloadFinished.set(true);
                } catch (Exception e) {
                  errors.add(e);
                }
              });

      List<Future<?>> workers = new ArrayList<>(1000);
      for (int i = 0; i < 1000; i++) {
        workers.add(
            executor.submit(
                () -> {
                  try {
                    while (!reloadFinished.get()) {
                      int r = registry.search(HI_KEY);
                      if (r != 1) {
                        throw new AssertionError("expected 1 before reload, got " + r);
                      }
                      preReloadSearches.incrementAndGet();
                    }
                    for (int k = 0; k < 200; k++) {
                      int r = registry.search(HI_KEY);
                      if (r != 42) {
                        throw new AssertionError("expected 42 after reload, got " + r);
                      }
                    }
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                }));
      }

      reloader.get(5L, TimeUnit.MINUTES);
      for (Future<?> f : workers) {
        f.get(5L, TimeUnit.MINUTES);
      }
    }

    assertThat(errors).isEmpty();
    assertThat(reloadFinished.get()).isTrue();
    assertThat(preReloadSearches.get()).isGreaterThanOrEqualTo(20_000);
  }

  private static Path writeSampleTrieDict(Path path, int hiLeafId) throws IOException {
    int nodeCount = 280;
    int[] base = new int[nodeCount];
    int[] check = new int[nodeCount];
    Arrays.fill(check, -1);
    check[1] = 0;
    base[1] = 1;
    check[105] = 1;
    base[105] = 1;
    check[106] = 105;
    base[106] = ~hiLeafId;
    check[231] = 1;
    base[231] = 81;
    check[232] = 231;
    base[232] = 68;
    check[233] = 232;
    base[233] = ~2;
    check[121] = 1;
    base[121] = ~3;
    long total = NativeDictionaryLayout.totalFileBytes(nodeCount);
    ByteBuffer bb = ByteBuffer.allocate((int) total).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(NativeDictionaryLayout.SUPPORTED_VERSION);
    bb.putInt(nodeCount);
    for (int v : base) {
      bb.putInt(v);
    }
    long pad = NativeDictionaryLayout.interArrayPaddingBytes(nodeCount);
    for (long i = 0L; i < pad; i++) {
      bb.put((byte) 0);
    }
    for (int v : check) {
      bb.putInt(v);
    }
    Files.write(path, bb.array());
    return path;
  }
}

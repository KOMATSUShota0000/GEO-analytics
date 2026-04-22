package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class DictionaryRegistryTest {

  private static Path writeNativeDictionary(
      Path path, int version, int nodeCount, int[] base, int[] check) throws IOException {
    if (base.length != nodeCount || check.length != nodeCount) {
      throw new IllegalArgumentException("array lengths must match nodeCount");
    }
    long total = NativeDictionaryLayout.totalFileBytes(nodeCount);
    if (total > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("test file too large");
    }
    ByteBuffer bb = ByteBuffer.allocate((int) total).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(version);
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

  private static void fillSampleTrie(int[] base, int[] check, int hiLeafId) {
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
  }

  private static Path writeTrieVersion(Path path, int hiLeafId) throws IOException {
    int nodeCount = 280;
    int[] base = new int[nodeCount];
    int[] check = new int[nodeCount];
    fillSampleTrie(base, check, hiLeafId);
    return writeNativeDictionary(
        path, NativeDictionaryLayout.SUPPORTED_VERSION, nodeCount, base, check);
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void virtualThreadsSearchWhileReloadCompletesWithoutFailures(@TempDir Path dir)
      throws Exception {
    Path v1 = dir.resolve("dict-v1.bin");
    Path v2 = dir.resolve("dict-v2.bin");
    writeTrieVersion(v1, 1);
    writeTrieVersion(v2, 100);

    try (DictionaryRegistry registry = new DictionaryRegistry(v1)) {
      assertThat(registry.search("hi")).isEqualTo(1);
      assertThat(registry.search("\u65E5")).isEqualTo(2);
      assertThat(registry.search("x")).isEqualTo(3);

      int taskCount = 512;
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicBoolean sawV1 = new AtomicBoolean(false);
      AtomicBoolean sawV2 = new AtomicBoolean(false);

      List<Future<?>> futures = new ArrayList<>(taskCount);
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < taskCount; i++) {
          Future<?> future =
              executor.submit(
                  () -> {
                    startLatch.await();
                    while (running.get()) {
                      int r = registry.search("hi");
                      if (r == 1) {
                        sawV1.set(true);
                      } else if (r == 100) {
                        sawV2.set(true);
                      } else {
                        throw new IllegalStateException("unexpected search result: " + r);
                      }
                      LockSupport.parkNanos(1_000L);
                    }
                    return null;
                  });
          futures.add(future);
        }

        startLatch.countDown();

        Thread reloader =
            Thread.ofVirtual()
                .start(
                    () -> {
                      try {
                        Thread.sleep(50L);
                        registry.reload(v2);
                      } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                      } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                      }
                    });
        reloader.join();

        Thread.sleep(200L);
        running.set(false);

        for (Future<?> future : futures) {
          future.get(60L, TimeUnit.SECONDS);
        }
      }

      assertThat(sawV1.get()).isTrue();
      assertThat(sawV2.get()).isTrue();
      assertThat(registry.search("hi")).isEqualTo(100);
      assertThat(registry.search("\u65E5")).isEqualTo(2);
      assertThat(registry.search("x")).isEqualTo(3);
    }
  }
}

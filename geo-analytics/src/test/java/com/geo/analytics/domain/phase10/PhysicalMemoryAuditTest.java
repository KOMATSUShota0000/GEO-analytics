package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * オフヒープ統計ブロックとゼロ・アロケーション・パイプラインの物理的制約を監査する。
 */
class PhysicalMemoryAuditTest {

  private static final int WARMUP_ITERATIONS = 150_000;
  private static final int MEASURED_ITERATIONS = 1_000_000;

  /**
   * JIT や VM 内部のごく小さな割り当てのみ許容（ホットループ自体がオブジェクトを積み上げないことの証明用）。
   */
  private static final long THREAD_ALLOC_SLACK_BYTES = 256L * 1024L;

  private static void triggerGcAndSettle() throws InterruptedException {
    System.gc();
    TimeUnit.MILLISECONDS.sleep(300L);
  }

  private static Path writeSampleTrieDict(Path dir) throws IOException {
    int nodeCount = 280;
    int[] base = new int[nodeCount];
    int[] check = new int[nodeCount];
    Arrays.fill(check, -1);
    check[1] = 0;
    base[1] = 1;
    check[105] = 1;
    base[105] = 1;
    check[106] = 105;
    base[106] = ~1;
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
    Path path = dir.resolve("physical-audit-dict.bin");
    Files.write(path, bb.array());
    return path;
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void hotPathProcessMessageAllocatesVirtuallyNothingAfterWarmup(@TempDir Path tempDir)
      throws Exception {
    ThreadMXBean threadMx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    assumeTrue(threadMx.isThreadAllocatedMemorySupported());
    threadMx.setThreadAllocatedMemoryEnabled(true);

    Path dictPath = writeSampleTrieDict(tempDir);
    byte[] payload = "hi".getBytes(StandardCharsets.US_ASCII);
    TenantContext tenant = new TenantContext(0, false);

    try (DictionaryRegistry registry = new DictionaryRegistry(dictPath);
        NativeStatsManager stats = new NativeStatsManager(2)) {
      ZeroAllocPipeline pipeline = new ZeroAllocPipeline(registry, stats);

      TenantScope.execute(
          tenant,
          () -> {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
              pipeline.processMessage(payload, 0, payload.length, 1.0d);
            }
          });

      triggerGcAndSettle();

      long threadId = Thread.currentThread().threadId();
      long allocatedBefore = threadMx.getThreadAllocatedBytes(threadId);

      TenantScope.execute(
          tenant,
          () -> {
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
              pipeline.processMessage(payload, 0, payload.length, 1.0d);
            }
          });

      long allocatedAfter = threadMx.getThreadAllocatedBytes(threadId);
      long delta = allocatedAfter - allocatedBefore;

      assertThat(delta)
          .as(
              "1e6 processMessage on hot path must not accumulate heap allocations (delta=%d bytes)",
              delta)
          .isLessThanOrEqualTo(THREAD_ALLOC_SLACK_BYTES);
    }

    triggerGcAndSettle();
  }

  @Test
  void offHeapBlockSizeIs256AndSegmentAddressIsCacheLineAligned() {
    assertThat(NativeStatsLayout.BLOCK_SIZE).isEqualTo(256L);
    try (NativeStatsManager manager = new NativeStatsManager(4)) {
      long addr = manager.segment().address();
      assertThat(addr % 128L).isZero();
    }
  }

  @Test
  void arenaCloseInvalidatesHandleAccess() {
    NativeStatsManager manager = new NativeStatsManager(4);
    NativeStatsHandle handle = manager.handle(0);
    manager.close();
    assertThatThrownBy(handle::getWelfordCount).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void asciiFlyweightAndPipelineRejectOutOfBoundsBeforeNativeRead(@TempDir Path dir)
      throws Exception {
    AsciiFlyweightCharSequence cs = new AsciiFlyweightCharSequence();
    byte[] buf = {0x41, 0x42, 0x43};

    assertThatThrownBy(() -> cs.set(buf, -1, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> cs.set(buf, 0, 10)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> cs.set(buf, 2, 2)).isInstanceOf(IndexOutOfBoundsException.class);

    Path dictPath = writeSampleTrieDict(dir);
    try (DictionaryRegistry registry = new DictionaryRegistry(dictPath);
        NativeStatsManager stats = new NativeStatsManager(1)) {
      ZeroAllocPipeline pipeline = new ZeroAllocPipeline(registry, stats);
      byte[] p = "x".getBytes(StandardCharsets.US_ASCII);
      TenantContext tenant = new TenantContext(0, false);
      assertThatThrownBy(
              () ->
                  TenantScope.execute(
                      tenant,
                      () -> pipeline.processMessage(p, -1, 1, 0.0d)))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(
              () ->
                  TenantScope.execute(
                      tenant,
                      () -> pipeline.processMessage(p, 0, 2, 0.0d)))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }
}

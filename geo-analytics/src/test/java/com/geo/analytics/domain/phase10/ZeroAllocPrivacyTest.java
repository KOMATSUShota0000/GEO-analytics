package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ZeroAllocPrivacyTest {

  private static final byte[] ID123_SECRET_ASCII = "ID:123-SECRET".getBytes(StandardCharsets.US_ASCII);

  @Test
  void asciiFlyweightVirtualMaskDoesNotMutateBackingBytes() {
    byte[] raw = ID123_SECRET_ASCII.clone();
    byte[] snapshot = raw.clone();

    AsciiFlyweightCharSequence view = new AsciiFlyweightCharSequence(raw, 0, raw.length);
    view.setMask(7, 6);

    for (int i = 7; i < 13; i++) {
      assertThat(view.charAt(i)).isEqualTo('*');
    }
    assertThat(view.charAt(6)).isEqualTo('-');
    assertThat(view.charAt(0)).isEqualTo('I');

    for (int i = 0; i < raw.length; i++) {
      assertThat(raw[i]).isEqualTo(snapshot[i]);
    }
    assertThat(raw[7]).isEqualTo((byte) 'S');
    assertThat(raw[8]).isEqualTo((byte) 'E');
    assertThat(raw[9]).isEqualTo((byte) 'C');

    CharSequence sub = view.subSequence(3, 10);
    assertThat(sub.length()).isEqualTo(7);
    assertThat(sub.charAt(0)).isEqualTo('1');
    assertThat(sub.charAt(3)).isEqualTo('-');
    assertThat(sub.charAt(4)).isEqualTo('*');
    assertThat(sub.charAt(5)).isEqualTo('*');
    assertThat(sub.charAt(6)).isEqualTo('*');
  }

  @Test
  void tenantScopePropagatesPiiPolicyAndDoesNotLeakAcrossNestedOrUnscopedThreads()
      throws Exception {
    TenantContext masked = new TenantContext(0, true);
    TenantScope.execute(
        masked,
        () -> {
          assertThat(TenantScope.currentRequiresPiiMasking()).isTrue();

          TenantContext innerPlain = new TenantContext(1, false);
          TenantScope.execute(
              innerPlain,
              () -> assertThat(TenantScope.currentRequiresPiiMasking()).isFalse());

          assertThat(TenantScope.currentRequiresPiiMasking()).isTrue();
        });

    assertThatThrownBy(TenantScope::currentRequiresPiiMasking)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not bound");

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      CountDownLatch outerRunning = new CountDownLatch(1);
      TenantScope.execute(
          new TenantContext(2, true),
          () -> {
            Future<?> foreign =
                executor.submit(
                    () -> {
                      assertThatThrownBy(TenantScope::currentRequiresPiiMasking)
                          .isInstanceOf(IllegalStateException.class);
                      return null;
                    });
            outerRunning.countDown();
            try {
              foreign.get(5L, TimeUnit.SECONDS);
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void zeroAllocPipelineAppliesVirtualMaskAfterPlaintextDictSearchAndPreservesBytes()
      throws Exception {
    DictionaryRegistry registry = mock(DictionaryRegistry.class);
    AtomicInteger searchCalls = new AtomicInteger();
    byte[] token = "PII-TOKEN".getBytes(StandardCharsets.US_ASCII);
    byte[] payload = token.clone();
    byte[] payloadSnapshot = token.clone();

    when(registry.search(any(CharSequence.class)))
        .thenAnswer(
            inv -> {
              searchCalls.incrementAndGet();
              CharSequence cs = inv.getArgument(0);
              assertThat(cs.length()).isEqualTo(9);
              for (int i = 0; i < 9; i++) {
                assertThat(cs.charAt(i)).isEqualTo((char) (payload[i] & 0xFF));
              }
              return 0;
            });

    try (NativeStatsManager stats = new NativeStatsManager(2)) {
      ZeroAllocPipeline pipeline = new ZeroAllocPipeline(registry, stats);
      NativeStatsUpdater updater = new NativeStatsUpdater(stats.handle(0));

      TenantContext tenant = new TenantContext(0, true);
      TenantScope.execute(
          tenant,
          () -> pipeline.processMessage(payload, 0, payload.length, 42.0d, 7, 2));

      assertThat(searchCalls.get()).isEqualTo(1);
      for (int i = 0; i < payload.length; i++) {
        assertThat(payload[i]).isEqualTo(payloadSnapshot[i]);
      }

      AsciiFlyweightCharSequence fw = extractFlyweight(pipeline);
      assertThat(fw.charAt(7)).isEqualTo('*');
      assertThat(fw.charAt(8)).isEqualTo('*');
      assertThat(fw.charAt(0)).isEqualTo('P');

      double[] buf = new double[3];
      int spins = 0;
      while (!updater.tryReadWelford(buf)) {
        Thread.onSpinWait();
        spins++;
        assertThat(spins).isLessThan(10_000_000);
      }
      assertThat(buf[0]).isEqualTo(1.0d);
      assertThat(buf[1]).isEqualTo(42.0d);
    }
  }

  @Test
  void whenPiiMaskingDisabledPipelineLeavesFlyweightUnmaskedDespiteMaskParams()
      throws Exception {
    DictionaryRegistry registry = mock(DictionaryRegistry.class);
    when(registry.search(any(CharSequence.class))).thenReturn(0);

    byte[] payload = "PII-TOKEN".getBytes(StandardCharsets.US_ASCII);

    try (NativeStatsManager stats = new NativeStatsManager(1)) {
      ZeroAllocPipeline pipeline = new ZeroAllocPipeline(registry, stats);
      TenantContext tenant = new TenantContext(0, false);
      TenantScope.execute(
          tenant,
          () -> pipeline.processMessage(payload, 0, payload.length, 1.0d, 7, 2));

      AsciiFlyweightCharSequence fw = extractFlyweight(pipeline);
      assertThat(fw.charAt(7)).isEqualTo('E');
      assertThat(fw.charAt(8)).isEqualTo('N');
    }
  }

  private static AsciiFlyweightCharSequence extractFlyweight(ZeroAllocPipeline pipeline)
      throws ReflectiveOperationException {
    Field f = ZeroAllocPipeline.class.getDeclaredField("flyweight");
    f.setAccessible(true);
    return (AsciiFlyweightCharSequence) f.get(pipeline);
  }
}

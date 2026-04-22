package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ParallelTaskOrchestratorTest {

  @Test
  void invokeAllOrderedRunsInParallelAndPreservesListOrder() throws Exception {
    List<String> results =
        ParallelTaskOrchestrator.invokeAllOrdered(
            List.of(
                () -> {
                  Thread.sleep(120L);
                  return "a";
                },
                () -> {
                  Thread.sleep(40L);
                  return "b";
                },
                () -> {
                  Thread.sleep(80L);
                  return "c";
                }));
    assertThat(results).containsExactly("a", "b", "c");
  }

  @Test
  void scopedValueTenantContextIsVisibleInAllForkedCallables() throws Exception {
    TenantScope.execute(
        new TenantContext(7, false),
        () -> {
          List<Integer> inner =
              ParallelTaskOrchestrator.invokeAllOrdered(
                  List.of(
                      () -> TenantScope.currentTenantIndex(),
                      () -> TenantScope.currentTenantIndex(),
                      () -> TenantScope.currentTenantIndex()));
          assertThat(inner).containsExactly(7, 7, 7);
        });
  }

  @Test
  void failFastCancelsPeersAndCompletesQuickly() throws Exception {
    CountDownLatch sawInterrupt2 = new CountDownLatch(1);
    CountDownLatch sawInterrupt3 = new CountDownLatch(1);
    Callable<String> first =
        () -> {
          Thread.sleep(100L);
          throw new IllegalStateException("first-failure");
        };
    Callable<String> slow2 =
        () -> {
          try {
            Thread.sleep(5000L);
          } catch (InterruptedException e) {
            sawInterrupt2.countDown();
            Thread.currentThread().interrupt();
            throw e;
          }
          return "two";
        };
    Callable<String> slow3 =
        () -> {
          try {
            Thread.sleep(5000L);
          } catch (InterruptedException e) {
            sawInterrupt3.countDown();
            Thread.currentThread().interrupt();
            throw e;
          }
          return "three";
        };
    long t0 = System.nanoTime();
    assertThatThrownBy(() -> ParallelTaskOrchestrator.invokeAllOrdered(List.of(first, slow2, slow3)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("first-failure");
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    assertThat(elapsedMs).isGreaterThanOrEqualTo(90L).isLessThan(3000L);
    assertThat(sawInterrupt2.await(2L, TimeUnit.SECONDS)).isTrue();
    assertThat(sawInterrupt3.await(2L, TimeUnit.SECONDS)).isTrue();
  }
}

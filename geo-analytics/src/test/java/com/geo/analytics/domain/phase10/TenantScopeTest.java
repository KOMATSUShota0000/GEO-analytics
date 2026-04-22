package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TenantScopeTest {

  @Test
  void currentTenantIndexOutsideScopeFailsFast() {
    assertThatThrownBy(TenantScope::currentTenantIndex)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not bound");
  }

  @Test
  void manyVirtualThreadsEachSeeOwnTenantInNestedCall() throws Exception {
    int threadCount = 400;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    List<Future<?>> futures = new ArrayList<>(threadCount);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int t = 0; t < threadCount; t++) {
        final int expectedTenant = t;
        Future<?> future =
            executor.submit(
                () -> {
                  try {
                    start.await();
                    TenantScope.execute(
                        new TenantContext(expectedTenant, false),
                        () ->
                            assertThat(readTenantDeeply()).isEqualTo(expectedTenant));
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  } finally {
                    done.countDown();
                  }
                  return null;
                });
        futures.add(future);
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(5L, TimeUnit.MINUTES);
      }
    }
    assertThat(done.await(1L, TimeUnit.MINUTES)).isTrue();
  }

  private static int readTenantDeeply() {
    return deeperTenantRead();
  }

  private static int deeperTenantRead() {
    return TenantScope.currentTenantIndex();
  }
}

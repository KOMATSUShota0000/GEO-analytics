package com.geo.analytics.domain.phase10;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class NativeStatsManager implements AutoCloseable {

  private static final long SEGMENT_ALIGNMENT = 128L;

  private final Arena arena;
  private final MemorySegment segment;
  private final int maxTenants;

  public NativeStatsManager(int maxTenants) {
    if (maxTenants < 1) {
      throw new IllegalArgumentException("maxTenants must be positive");
    }
    this.maxTenants = maxTenants;
    this.arena = Arena.ofShared();
    long bytes = Math.multiplyExact((long) maxTenants, NativeStatsLayout.BLOCK_SIZE);
    this.segment = arena.allocate(bytes, SEGMENT_ALIGNMENT);
  }

  public MemorySegment segment() {
    return segment;
  }

  public int maxTenants() {
    return maxTenants;
  }

  public NativeStatsHandle handle(int tenantIndex) {
    Objects.checkIndex(tenantIndex, maxTenants);
    return new NativeStatsHandle(segment, tenantIndex);
  }

  @Override
  public void close() {
    arena.close();
  }
}

package com.geo.analytics.domain.phase10;

import java.util.Objects;

public final class ZeroAllocPipeline {

  private final DictionaryRegistry registry;
  private final NativeStatsUpdater[] updaters;
  private final AsciiFlyweightCharSequence flyweight;

  public ZeroAllocPipeline(DictionaryRegistry registry, NativeStatsManager statsManager) {
    this.registry = Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(statsManager, "statsManager");
    int n = statsManager.maxTenants();
    NativeStatsUpdater[] built = new NativeStatsUpdater[n];
    for (int i = 0; i < n; i++) {
      built[i] = new NativeStatsUpdater(statsManager.handle(i));
    }
    this.updaters = built;
    this.flyweight = new AsciiFlyweightCharSequence();
  }

  public void processMessage(byte[] payload, int offset, int length, double score) {
    processMessage(payload, offset, length, score, -1, 0);
  }

  /**
   * @param piiMaskStart first index (relative to {@code offset..offset+length}) to virtual-mask; ignored
   *     if {@code piiMaskLength} &lt;= 0 or tenant policy does not require masking
   * @param piiMaskLength number of indices to expose as {@code '*'} via {@link AsciiFlyweightCharSequence}
   */
  public void processMessage(
      byte[] payload, int offset, int length, double score, int piiMaskStart, int piiMaskLength) {
    Objects.requireNonNull(payload, "payload");
    Objects.checkFromIndexSize(offset, length, payload.length);
    int tenantIndex = TenantScope.currentTenantIndex();
    flyweight.set(payload, offset, length);
    int dictId = registry.search(flyweight);
    if (TenantScope.currentRequiresPiiMasking() && piiMaskLength > 0) {
      flyweight.setMask(piiMaskStart, piiMaskLength);
    }
    if (dictId >= 0) {
      updaters[tenantIndex].observeWelford(score);
    }
  }
}

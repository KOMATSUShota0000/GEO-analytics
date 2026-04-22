package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import org.junit.jupiter.api.Test;

class NativeStatsLayoutTest {

  @Test
  void blockSizeIs256AndMultipleOf128() {
    assertThat(NativeStatsLayout.LOGICAL_BYTE_SIZE).isEqualTo(136L);
    assertThat(NativeStatsLayout.TERMINAL_PADDING_BYTES).isEqualTo(120L);
    assertThat(NativeStatsLayout.BLOCK_STRIDE).isEqualTo(256L);
    assertThat(NativeStatsLayout.BLOCK_SIZE).isEqualTo(256L);
    assertThat(NativeStatsLayout.BLOCK_SIZE % 128L).isEqualTo(0L);
    assertThat(NativeStatsLayout.BLOCK_LAYOUT.byteSize()).isEqualTo(256L);
  }

  @Test
  void tenantsDoNotInterfereWhenWrittenViaVarHandles() {
    int maxTenants = 10;
    try (NativeStatsManager manager = new NativeStatsManager(maxTenants)) {
      assertThat(manager.segment().byteSize()).isEqualTo(2560L);
      NativeStatsHandle t0 = manager.handle(0);
      NativeStatsHandle t1 = manager.handle(1);
      t0.setSeqLock(111L);
      t0.setWelfordCount(222L);
      t1.setSeqLock(999L);
      t1.setWelfordCount(888L);
      assertThat(t0.getSeqLock()).isEqualTo(111L);
      assertThat(t0.getWelfordCount()).isEqualTo(222L);
      assertThat(t1.getSeqLock()).isEqualTo(999L);
      assertThat(t1.getWelfordCount()).isEqualTo(888L);
      MemorySegment seg = manager.segment();
      VarHandle longH = NativeStatsLayout.LONG_LE_HANDLE;
      long off0 = (long) 0 * NativeStatsLayout.BLOCK_SIZE + NativeStatsLayout.SEQ_LOCK_OFFSET;
      long off1 = (long) 1 * NativeStatsLayout.BLOCK_SIZE + NativeStatsLayout.SEQ_LOCK_OFFSET;
      assertThat((long) longH.get(seg, off0)).isEqualTo(111L);
      assertThat((long) longH.get(seg, off1)).isEqualTo(999L);
      long wc0 =
          (long) 0 * NativeStatsLayout.BLOCK_SIZE + NativeStatsLayout.WELFORD_COUNT_OFFSET;
      long wc1 =
          (long) 1 * NativeStatsLayout.BLOCK_SIZE + NativeStatsLayout.WELFORD_COUNT_OFFSET;
      assertThat((long) longH.get(seg, wc0)).isEqualTo(222L);
      assertThat((long) longH.get(seg, wc1)).isEqualTo(888L);
    }
  }
}

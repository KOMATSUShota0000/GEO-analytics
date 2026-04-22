package com.geo.analytics.domain.phase10;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public final class NativeStatsLayout {

  public static final ValueLayout.OfInt JAVA_INT_LE =
      ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);
  public static final ValueLayout.OfLong JAVA_LONG_LE =
      ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN);
  public static final ValueLayout.OfDouble JAVA_DOUBLE_LE =
      ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN);

  public static final long VERSION_OFFSET = 0L;
  public static final long FLAGS_OFFSET = 4L;
  public static final long SEQ_LOCK_OFFSET = 8L;
  public static final long WELFORD_COUNT_OFFSET = 16L;
  public static final long WELFORD_MEAN_OFFSET = 24L;
  public static final long WELFORD_M2_OFFSET = 32L;
  public static final long PSQ_N_OFFSET = 40L;
  public static final long PSQ_Q_OFFSET = 80L;
  public static final long ZIB_PROCESSED_COUNT_OFFSET = 120L;
  public static final long ZIB_SUM_OF_SCORES_OFFSET = 128L;

  private static final MemoryLayout CORE_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_INT_LE.withName("version"),
          JAVA_INT_LE.withName("flags"),
          JAVA_LONG_LE.withName("seqLock"),
          JAVA_LONG_LE.withName("welfordCount"),
          JAVA_DOUBLE_LE.withName("welfordMean"),
          JAVA_DOUBLE_LE.withName("welfordM2"),
          MemoryLayout.sequenceLayout(5, JAVA_LONG_LE).withName("psqN"),
          MemoryLayout.sequenceLayout(5, JAVA_DOUBLE_LE).withName("psqQ"),
          JAVA_LONG_LE.withName("zibProcessedCount"),
          JAVA_DOUBLE_LE.withName("zibSumOfScores"));

  public static final long LOGICAL_BYTE_SIZE = CORE_LAYOUT.byteSize();
  public static final long CACHE_LINE_STRIDE = 128L;
  public static final long BLOCK_STRIDE =
      ((LOGICAL_BYTE_SIZE + CACHE_LINE_STRIDE - 1L) / CACHE_LINE_STRIDE)
          * CACHE_LINE_STRIDE;
  public static final long TERMINAL_PADDING_BYTES = BLOCK_STRIDE - LOGICAL_BYTE_SIZE;
  public static final long PADDING_OFFSET = LOGICAL_BYTE_SIZE;

  public static final MemoryLayout BLOCK_LAYOUT =
      MemoryLayout.structLayout(
          CORE_LAYOUT, MemoryLayout.paddingLayout(TERMINAL_PADDING_BYTES));

  public static final long BLOCK_SIZE = BLOCK_LAYOUT.byteSize();

  public static final VarHandle INT_LE_HANDLE = JAVA_INT_LE.varHandle();
  public static final VarHandle LONG_LE_HANDLE = JAVA_LONG_LE.varHandle();
  public static final VarHandle DOUBLE_LE_HANDLE = JAVA_DOUBLE_LE.varHandle();

  private NativeStatsLayout() {}
}

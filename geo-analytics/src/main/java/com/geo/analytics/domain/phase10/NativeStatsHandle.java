package com.geo.analytics.domain.phase10;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public record NativeStatsHandle(MemorySegment segment, int tenantIndex, long blockBaseOffset) {

  public NativeStatsHandle(MemorySegment segment, int tenantIndex) {
    this(
        segment,
        tenantIndex,
        Math.multiplyExact((long) tenantIndex, NativeStatsLayout.BLOCK_SIZE));
  }

  public int getVersion() {
    return (int)
        NativeStatsLayout.INT_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.VERSION_OFFSET);
  }

  public void setVersion(int value) {
    NativeStatsLayout.INT_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.VERSION_OFFSET, value);
  }

  public int getFlags() {
    return (int)
        NativeStatsLayout.INT_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.FLAGS_OFFSET);
  }

  public void setFlags(int value) {
    NativeStatsLayout.INT_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.FLAGS_OFFSET, value);
  }

  public long getSeqLock() {
    return (long)
        NativeStatsLayout.LONG_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.SEQ_LOCK_OFFSET);
  }

  public void setSeqLock(long value) {
    NativeStatsLayout.LONG_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.SEQ_LOCK_OFFSET, value);
  }

  public long getWelfordCount() {
    return (long)
        NativeStatsLayout.LONG_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.WELFORD_COUNT_OFFSET);
  }

  public void setWelfordCount(long value) {
    NativeStatsLayout.LONG_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.WELFORD_COUNT_OFFSET, value);
  }

  public double getWelfordMean() {
    return (double)
        NativeStatsLayout.DOUBLE_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.WELFORD_MEAN_OFFSET);
  }

  public void setWelfordMean(double value) {
    NativeStatsLayout.DOUBLE_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.WELFORD_MEAN_OFFSET, value);
  }

  public double getWelfordM2() {
    return (double)
        NativeStatsLayout.DOUBLE_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.WELFORD_M2_OFFSET);
  }

  public void setWelfordM2(double value) {
    NativeStatsLayout.DOUBLE_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.WELFORD_M2_OFFSET, value);
  }

  public long getPsqN(int index) {
    Objects.checkIndex(index, 5);
    long off = blockBaseOffset + NativeStatsLayout.PSQ_N_OFFSET + (long) index * Long.BYTES;
    return (long) NativeStatsLayout.LONG_LE_HANDLE.get(segment, off);
  }

  public void setPsqN(int index, long value) {
    Objects.checkIndex(index, 5);
    long off = blockBaseOffset + NativeStatsLayout.PSQ_N_OFFSET + (long) index * Long.BYTES;
    NativeStatsLayout.LONG_LE_HANDLE.set(segment, off, value);
  }

  public double getPsqQ(int index) {
    Objects.checkIndex(index, 5);
    long off = blockBaseOffset + NativeStatsLayout.PSQ_Q_OFFSET + (long) index * Double.BYTES;
    return (double) NativeStatsLayout.DOUBLE_LE_HANDLE.get(segment, off);
  }

  public void setPsqQ(int index, double value) {
    Objects.checkIndex(index, 5);
    long off = blockBaseOffset + NativeStatsLayout.PSQ_Q_OFFSET + (long) index * Double.BYTES;
    NativeStatsLayout.DOUBLE_LE_HANDLE.set(segment, off, value);
  }

  public long getZibProcessedCount() {
    return (long)
        NativeStatsLayout.LONG_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.ZIB_PROCESSED_COUNT_OFFSET);
  }

  public void setZibProcessedCount(long value) {
    NativeStatsLayout.LONG_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.ZIB_PROCESSED_COUNT_OFFSET, value);
  }

  public double getZibSumOfScores() {
    return (double)
        NativeStatsLayout.DOUBLE_LE_HANDLE.get(
            segment, blockBaseOffset + NativeStatsLayout.ZIB_SUM_OF_SCORES_OFFSET);
  }

  public void setZibSumOfScores(double value) {
    NativeStatsLayout.DOUBLE_LE_HANDLE.set(
        segment, blockBaseOffset + NativeStatsLayout.ZIB_SUM_OF_SCORES_OFFSET, value);
  }
}

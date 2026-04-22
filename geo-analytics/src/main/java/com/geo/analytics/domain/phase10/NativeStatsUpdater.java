package com.geo.analytics.domain.phase10;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

public final class NativeStatsUpdater {

  private final MemorySegment segment;
  private final long seqOff;
  private final long countOff;
  private final long meanOff;
  private final long m2Off;

  public NativeStatsUpdater(NativeStatsHandle handle) {
    Objects.requireNonNull(handle, "handle");
    this.segment = handle.segment();
    long base = handle.blockBaseOffset();
    this.seqOff = base + NativeStatsLayout.SEQ_LOCK_OFFSET;
    this.countOff = base + NativeStatsLayout.WELFORD_COUNT_OFFSET;
    this.meanOff = base + NativeStatsLayout.WELFORD_MEAN_OFFSET;
    this.m2Off = base + NativeStatsLayout.WELFORD_M2_OFFSET;
  }

  public void observeWelford(double value) {
    lockSeq();
    try {
      VarHandle longH = NativeStatsLayout.LONG_LE_HANDLE;
      VarHandle doubleH = NativeStatsLayout.DOUBLE_LE_HANDLE;
      long n = (long) longH.get(segment, countOff);
      double mean = (double) doubleH.get(segment, meanOff);
      double m2 = (double) doubleH.get(segment, m2Off);
      if (n <= 0L) {
        longH.set(segment, countOff, 1L);
        doubleH.set(segment, meanOff, value);
        doubleH.set(segment, m2Off, 0.0d);
      } else {
        long n1 = n + 1L;
        double delta = value - mean;
        double mean1 = mean + delta / (double) n1;
        double delta2 = value - mean1;
        double m2_1 = m2 + delta * delta2;
        longH.set(segment, countOff, n1);
        doubleH.set(segment, meanOff, mean1);
        doubleH.set(segment, m2Off, m2_1);
      }
    } finally {
      unlockSeq();
    }
  }

  public boolean tryReadWelford(double[] outBuffer) {
    Objects.requireNonNull(outBuffer, "outBuffer");
    if (outBuffer.length < 3) {
      throw new IllegalArgumentException("outBuffer.length must be at least 3");
    }
    VarHandle longH = NativeStatsLayout.LONG_LE_HANDLE;
    VarHandle doubleH = NativeStatsLayout.DOUBLE_LE_HANDLE;
    long s1 = (long) longH.getAcquire(segment, seqOff);
    if ((s1 & 1L) != 0L) {
      return false;
    }
    long n = (long) longH.getAcquire(segment, countOff);
    double mean = (double) doubleH.getAcquire(segment, meanOff);
    double m2 = (double) doubleH.getAcquire(segment, m2Off);
    long s2 = (long) longH.getAcquire(segment, seqOff);
    if (s1 != s2) {
      return false;
    }
    if ((s2 & 1L) != 0L) {
      return false;
    }
    outBuffer[0] = (double) n;
    outBuffer[1] = mean;
    outBuffer[2] = m2;
    return true;
  }

  private void lockSeq() {
    VarHandle vh = NativeStatsLayout.LONG_LE_HANDLE;
    int spins = 0;
    for (; ; ) {
      long s = (long) vh.getAcquire(segment, seqOff);
      if ((s & 1L) != 0L) {
        Thread.onSpinWait();
        spins++;
        if (spins % 100 == 0) {
          LockSupport.parkNanos(1_000L);
        }
        continue;
      }
      if (vh.compareAndSet(segment, seqOff, s, s + 1L)) {
        return;
      }
      Thread.onSpinWait();
      spins++;
      if (spins % 100 == 0) {
        LockSupport.parkNanos(1_000L);
      }
    }
  }

  private void unlockSeq() {
    NativeStatsLayout.LONG_LE_HANDLE.getAndAddRelease(segment, seqOff, 1L);
  }
}

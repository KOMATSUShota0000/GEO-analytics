package com.geo.analytics.domain.phase10;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class NativeDictionaryLayout {

  public static final int SUPPORTED_VERSION = 1;

  public static final ValueLayout.OfInt INT_LE =
      ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);

  public static final MemoryLayout HEADER_LAYOUT =
      MemoryLayout.structLayout(
          INT_LE.withName("VERSION"), INT_LE.withName("NODE_COUNT"));

  private NativeDictionaryLayout() {}

  public static long interArrayPaddingBytes(int nodeCount) {
    if (nodeCount < 0) {
      throw new IllegalArgumentException("nodeCount must be non-negative");
    }
    long baseBytes = (long) nodeCount * (long) Integer.BYTES;
    long rem = baseBytes % Long.BYTES;
    return rem == 0L ? 0L : Long.BYTES - rem;
  }

  public static MemoryLayout dictionaryLayout(int nodeCount) {
    if (nodeCount < 0) {
      throw new IllegalArgumentException("nodeCount must be non-negative");
    }
    long pad = interArrayPaddingBytes(nodeCount);
    List<MemoryLayout> parts = new ArrayList<>(5);
    parts.add(INT_LE.withName("VERSION"));
    parts.add(INT_LE.withName("NODE_COUNT"));
    parts.add(MemoryLayout.sequenceLayout(nodeCount, INT_LE).withName("base"));
    if (pad > 0L) {
      parts.add(MemoryLayout.paddingLayout(pad));
    }
    parts.add(MemoryLayout.sequenceLayout(nodeCount, INT_LE).withName("check"));
    return MemoryLayout.structLayout(parts.toArray(MemoryLayout[]::new));
  }

  public static long totalFileBytes(int nodeCount) {
    return dictionaryLayout(nodeCount).byteSize();
  }

  public static long baseArrayOffsetBytes() {
    return HEADER_LAYOUT.byteSize();
  }

  public static long checkArrayOffsetBytes(int nodeCount) {
    long baseEnd = baseArrayOffsetBytes() + (long) nodeCount * (long) Integer.BYTES;
    return baseEnd + interArrayPaddingBytes(nodeCount);
  }
}

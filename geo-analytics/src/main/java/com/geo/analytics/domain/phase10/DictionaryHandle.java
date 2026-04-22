package com.geo.analytics.domain.phase10;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.Objects;

public record DictionaryHandle(
    Arena arena,
    MemorySegment segment,
    int version,
    int nodeCount,
    long baseOffsetBytes,
    long checkOffsetBytes,
    VarHandle intLeHandle)
    implements AutoCloseable {

  @Override
  public void close() {
    arena.close();
  }

  public int readBase(int index) {
    Objects.checkIndex(index, nodeCount);
    return (int)
        intLeHandle.get(segment, baseOffsetBytes + (long) index * Integer.BYTES);
  }

  public int readCheck(int index) {
    Objects.checkIndex(index, nodeCount);
    return (int)
        intLeHandle.get(segment, checkOffsetBytes + (long) index * Integer.BYTES);
  }
}

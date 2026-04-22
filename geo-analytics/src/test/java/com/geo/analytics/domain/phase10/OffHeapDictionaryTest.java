package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OffHeapDictionaryTest {

  private static Path writeNativeDictionary(int version, int nodeCount, int[] base, int[] check)
      throws IOException {
    if (base.length != nodeCount || check.length != nodeCount) {
      throw new IllegalArgumentException("array lengths must match nodeCount");
    }
    long total = NativeDictionaryLayout.totalFileBytes(nodeCount);
    if (total > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("test file too large");
    }
    ByteBuffer bb = ByteBuffer.allocate((int) total).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(version);
    bb.putInt(nodeCount);
    for (int v : base) {
      bb.putInt(v);
    }
    long pad = NativeDictionaryLayout.interArrayPaddingBytes(nodeCount);
    for (long i = 0L; i < pad; i++) {
      bb.put((byte) 0);
    }
    for (int v : check) {
      bb.putInt(v);
    }
    Path path = Files.createTempFile("offheap-dict-", ".bin");
    Files.write(path, bb.array());
    return path;
  }

  @Test
  void loadMapsFileAndVarHandleReadsMatchWrittenBaseAndCheck() throws IOException {
    int nodeCount = 3;
    int[] base = {10, 20, 30};
    int[] check = {100, 200, 300};
    Path path = writeNativeDictionary(NativeDictionaryLayout.SUPPORTED_VERSION, nodeCount, base, check);
    try {
      OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
      try (DictionaryHandle handle = manager.load(path)) {
        assertThat(handle.version()).isEqualTo(NativeDictionaryLayout.SUPPORTED_VERSION);
        assertThat(handle.nodeCount()).isEqualTo(nodeCount);
        VarHandle vh = handle.intLeHandle();
        MemorySegment segment = handle.segment();
        for (int i = 0; i < nodeCount; i++) {
          long baseAddr = handle.baseOffsetBytes() + (long) i * Integer.BYTES;
          long checkAddr = handle.checkOffsetBytes() + (long) i * Integer.BYTES;
          assertThat((int) vh.get(segment, baseAddr)).isEqualTo(base[i]);
          assertThat((int) vh.get(segment, checkAddr)).isEqualTo(check[i]);
          assertThat(handle.readBase(i)).isEqualTo(base[i]);
          assertThat(handle.readCheck(i)).isEqualTo(check[i]);
        }
      }
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void accessAfterCloseThrowsIllegalStateException() throws IOException {
    int nodeCount = 2;
    int[] base = {1, 2};
    int[] check = {3, 4};
    Path path = writeNativeDictionary(NativeDictionaryLayout.SUPPORTED_VERSION, nodeCount, base, check);
    try {
      OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
      DictionaryHandle handle = manager.load(path);
      MemorySegment segment = handle.segment();
      VarHandle vh = handle.intLeHandle();
      long addr = handle.baseOffsetBytes();
      handle.close();
      assertThatThrownBy(() -> vh.get(segment, addr)).isInstanceOf(IllegalStateException.class);
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void wrongVersionThrowsDictionaryValidationException() throws IOException {
    int nodeCount = 1;
    int[] base = {0};
    int[] check = {0};
    Path path = writeNativeDictionary(999, nodeCount, base, check);
    try {
      OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
      assertThatThrownBy(() -> manager.load(path))
          .isInstanceOf(DictionaryValidationException.class)
          .hasMessageContaining("unsupported dictionary version");
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void fileSizeMismatchThrowsDictionaryValidationException() throws IOException {
    Path path = Files.createTempFile("offheap-dict-bad-", ".bin");
    try {
      ByteBuffer bb = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
      bb.putInt(NativeDictionaryLayout.SUPPORTED_VERSION);
      bb.putInt(10);
      bb.putInt(0);
      Files.write(path, bb.array());
      OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
      assertThatThrownBy(() -> manager.load(path))
          .isInstanceOf(DictionaryValidationException.class)
          .hasMessageContaining("file size mismatch");
    } finally {
      Files.deleteIfExists(path);
    }
  }
}

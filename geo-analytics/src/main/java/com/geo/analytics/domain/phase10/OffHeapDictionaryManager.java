package com.geo.analytics.domain.phase10;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class OffHeapDictionaryManager {

  private static final VarHandle INT_LE_HANDLE = NativeDictionaryLayout.INT_LE.varHandle();

  public DictionaryHandle load(Path path) throws IOException {
    long fileSize = Files.size(path);
    if (fileSize < NativeDictionaryLayout.HEADER_LAYOUT.byteSize()) {
      throw new DictionaryValidationException(
          "dictionary file too small: path="
              + path
              + " fileSize="
              + fileSize
              + " minimum="
              + NativeDictionaryLayout.HEADER_LAYOUT.byteSize());
    }
    Arena arena = Arena.ofShared();
    boolean ok = false;
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      MemorySegment segment =
          channel.map(FileChannel.MapMode.READ_ONLY, 0L, fileSize, arena);
      int version = (int) INT_LE_HANDLE.get(segment, 0L);
      int nodeCount = (int) INT_LE_HANDLE.get(segment, Integer.BYTES);
      if (version != NativeDictionaryLayout.SUPPORTED_VERSION) {
        throw new DictionaryValidationException(
            "unsupported dictionary version: path="
                + path
                + " version="
                + version
                + " supported="
                + NativeDictionaryLayout.SUPPORTED_VERSION);
      }
      if (nodeCount < 0) {
        throw new DictionaryValidationException(
            "invalid NODE_COUNT: path=" + path + " nodeCount=" + nodeCount);
      }
      long expectedSize;
      try {
        expectedSize = NativeDictionaryLayout.totalFileBytes(nodeCount);
      } catch (ArithmeticException ex) {
        throw new DictionaryValidationException(
            "NODE_COUNT overflow in layout computation: path="
                + path
                + " nodeCount="
                + nodeCount);
      }
      if (expectedSize < 0L || expectedSize != fileSize) {
        throw new DictionaryValidationException(
            "dictionary file size mismatch: path="
                + path
                + " fileSize="
                + fileSize
                + " expectedForNodeCount="
                + expectedSize
                + " nodeCount="
                + nodeCount);
      }
      long baseOffset = NativeDictionaryLayout.baseArrayOffsetBytes();
      long checkOffset = NativeDictionaryLayout.checkArrayOffsetBytes(nodeCount);
      DictionaryHandle handle =
          new DictionaryHandle(
              arena, segment, version, nodeCount, baseOffset, checkOffset, INT_LE_HANDLE);
      ok = true;
      return handle;
    } finally {
      if (!ok) {
        arena.close();
      }
    }
  }
}

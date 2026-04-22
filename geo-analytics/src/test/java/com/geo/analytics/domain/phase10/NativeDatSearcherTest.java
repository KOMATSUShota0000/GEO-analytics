package com.geo.analytics.domain.phase10;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeDatSearcherTest {

  private static Path writeNativeDictionary(
      Path dir, int version, int nodeCount, int[] base, int[] check) throws IOException {
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
    Path path = dir.resolve("native-dat-dict.bin");
    Files.write(path, bb.array());
    return path;
  }

  private static int[][] emptyTrie(int nodeCount) {
    int[] base = new int[nodeCount];
    int[] check = new int[nodeCount];
    Arrays.fill(check, -1);
    return new int[][] {base, check};
  }

  private static DictionaryHandle loadSampleTrie(Path dir) throws IOException {
    int nodeCount = 280;
    int[][] pair = emptyTrie(nodeCount);
    int[] base = pair[0];
    int[] check = pair[1];
    check[1] = 0;
    base[1] = 1;
    check[105] = 1;
    base[105] = 1;
    check[106] = 105;
    base[106] = ~1;
    check[231] = 1;
    base[231] = 81;
    check[232] = 231;
    base[232] = 68;
    check[233] = 232;
    base[233] = ~2;
    check[121] = 1;
    base[121] = ~3;
    Path path =
        writeNativeDictionary(dir, NativeDictionaryLayout.SUPPORTED_VERSION, nodeCount, base, check);
    OffHeapDictionaryManager manager = new OffHeapDictionaryManager();
    return manager.load(path);
  }

  @Test
  void findExactReturnsIdsForAsciiAndMultibyteKeys(@TempDir Path dir) throws Exception {
    try (DictionaryHandle handle = loadSampleTrie(dir)) {
      NativeDatSearcher searcher = new NativeDatSearcher(handle);
      assertThat(searcher.findExact("hi")).isEqualTo(1);
      assertThat(searcher.findExact("\u65E5")).isEqualTo(2);
      assertThat(searcher.findExact("x")).isEqualTo(3);
    }
  }

  @Test
  void findExactReturnsMinusOneForMissingKeys(@TempDir Path dir) throws Exception {
    try (DictionaryHandle handle = loadSampleTrie(dir)) {
      NativeDatSearcher searcher = new NativeDatSearcher(handle);
      assertThat(searcher.findExact("cat")).isEqualTo(-1);
      assertThat(searcher.findExact("hz")).isEqualTo(-1);
      assertThat(searcher.findExact("")).isEqualTo(-1);
    }
  }

  @Test
  void findExactReturnsMinusOneForPrefixOnlyMatch(@TempDir Path dir) throws Exception {
    try (DictionaryHandle handle = loadSampleTrie(dir)) {
      NativeDatSearcher searcher = new NativeDatSearcher(handle);
      assertThat(searcher.findExact("h")).isEqualTo(-1);
    }
  }

  @Test
  void findExactReturnsMinusOneWhenExtraSuffixAfterLeaf(@TempDir Path dir) throws Exception {
    try (DictionaryHandle handle = loadSampleTrie(dir)) {
      NativeDatSearcher searcher = new NativeDatSearcher(handle);
      assertThat(searcher.findExact("hi!")).isEqualTo(-1);
    }
  }
}

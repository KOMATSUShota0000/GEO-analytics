package com.geo.analytics.domain.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OffHeapDoubleArrayTrieTest {

    private static final String KYANON = "\u30ad\u30e4\u30ce\u30f3";

    private static final String SONY_KATAKANA = "\u30bd\u30cb\u30fc";

    private static final String FUJIFILM = "\u5bcc\u58eb\u30d5\u30a4\u30eb\u30e0";

    private static final String PANASONIC = "\u30d1\u30ca\u30bd\u30cb\u30c3\u30af";

    private static final String KYANO_PARTIAL = "\u30ad\u30e4\u30ce";

    private static List<String> sampleDictionary() {
        return List.of(KYANON, SONY_KATAKANA, FUJIFILM);
    }

    @Test
    void shouldBuildFromValidDictionaryAndResolveContainsCorrectly() {
        try (OffHeapDoubleArrayTrie trie = new OffHeapDoubleArrayTrie.Builder().build(sampleDictionary())) {
            assertThat(trie.contains(KYANON)).isTrue();
            assertThat(trie.contains(SONY_KATAKANA)).isTrue();
            assertThat(trie.contains(FUJIFILM)).isTrue();
            assertThat(trie.contains(PANASONIC)).isFalse();
            assertThat(trie.contains(KYANO_PARTIAL)).isFalse();
        }
    }

    @Test
    void shouldRejectNullDictionaryInBuilder() {
        assertThatThrownBy(() -> new OffHeapDoubleArrayTrie.Builder().build(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDictionaryContainingNullElement() {
        List<String> withNull = new ArrayList<>();
        withNull.add(KYANON);
        withNull.add(null);
        assertThatThrownBy(() -> new OffHeapDoubleArrayTrie.Builder().build(withNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnFalseForNullTextInContains() {
        try (OffHeapDoubleArrayTrie trie = new OffHeapDoubleArrayTrie.Builder().build(sampleDictionary())) {
            assertThat(trie.contains(null)).isFalse();
        }
    }

    @Test
    void shouldReturnFalseAfterCloseWithoutUseAfterFreeCrash() {
        OffHeapDoubleArrayTrie trie = new OffHeapDoubleArrayTrie.Builder().build(sampleDictionary());
        trie.close();
        assertThat(trie.contains(KYANON)).isFalse();
        assertThat(trie.contains(SONY_KATAKANA)).isFalse();
        assertThat(trie.contains(FUJIFILM)).isFalse();
        assertThat(trie.contains(null)).isFalse();
    }

    @Test
    void shouldReturnAccurateContainsUnderHeavyConcurrentVirtualThreadLoad() throws Exception {
        try (OffHeapDoubleArrayTrie trie = new OffHeapDoubleArrayTrie.Builder().build(sampleDictionary())) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                int threadCount = 128;
                List<Callable<Boolean>> tasks = new ArrayList<>(threadCount);
                for (int t = 0; t < threadCount; t++) {
                    tasks.add(
                            () -> {
                                for (int r = 0; r < 500; r++) {
                                    if (!trie.contains(KYANON)) {
                                        return false;
                                    }
                                    if (!trie.contains(SONY_KATAKANA)) {
                                        return false;
                                    }
                                    if (!trie.contains(FUJIFILM)) {
                                        return false;
                                    }
                                    if (trie.contains(PANASONIC)) {
                                        return false;
                                    }
                                    if (trie.contains(KYANO_PARTIAL)) {
                                        return false;
                                    }
                                    if (trie.contains(null)) {
                                        return false;
                                    }
                                }
                                return true;
                            });
                }
                List<Future<Boolean>> futures = executor.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    assertThat(future.get()).isTrue();
                }
            }
        }
    }
}

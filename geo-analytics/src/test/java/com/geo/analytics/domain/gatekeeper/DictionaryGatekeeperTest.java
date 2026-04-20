package com.geo.analytics.domain.gatekeeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.trie.OffHeapDoubleArrayTrie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DictionaryGatekeeperTest {

    @Test
    void shouldReturnImmediateMissWithNullNormalizedTextWhenRawTextIsNull() {
        OffHeapDoubleArrayTrie trie = mock(OffHeapDoubleArrayTrie.class);
        DictionaryGatekeeper gatekeeper = new DictionaryGatekeeper(trie);
        DictionaryGatekeeper.GatekeeperResult result = gatekeeper.evaluate(null);
        assertThat(result.dictionaryHit()).isFalse();
        assertThat(result.confidence()).isNaN();
        assertThat(result.bypassLayer1()).isFalse();
        assertThat(result.normalizedText()).isNull();
    }

    @Test
    void shouldShortCircuitWithDictionaryHitConfidenceOneAndBypassWhenTrieContainsNormalized() {
        OffHeapDoubleArrayTrie trie = mock(OffHeapDoubleArrayTrie.class);
        when(trie.contains("\u30ad\u30e4\u30ce\u30f3")).thenReturn(true);
        DictionaryGatekeeper gatekeeper = new DictionaryGatekeeper(trie);
        String raw = "\u682a\u5f0f\u4f1a\u793e\u30ad\u30e4\u30ce\u30f3";
        DictionaryGatekeeper.GatekeeperResult result = gatekeeper.evaluate(raw);
        assertThat(result.dictionaryHit()).isTrue();
        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.bypassLayer1()).isTrue();
        assertThat(result.normalizedText()).isEqualTo("\u30ad\u30e4\u30ce\u30f3");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(trie).contains(captor.capture());
        assertThat(captor.getValue()).isEqualTo("\u30ad\u30e4\u30ce\u30f3");
    }

    @Test
    void shouldReturnMissWithNaNConfidenceNoBypassAndNormalizedTextWhenTrieMisses() {
        OffHeapDoubleArrayTrie trie = mock(OffHeapDoubleArrayTrie.class);
        when(trie.contains(anyString())).thenReturn(false);
        DictionaryGatekeeper gatekeeper = new DictionaryGatekeeper(trie);
        String raw = "\u672a\u77e5\u8a9e";
        DictionaryGatekeeper.GatekeeperResult result = gatekeeper.evaluate(raw);
        assertThat(result.dictionaryHit()).isFalse();
        assertThat(result.confidence()).isNaN();
        assertThat(result.bypassLayer1()).isFalse();
        assertThat(result.normalizedText()).isEqualTo("\u672a\u77e5\u8a9e");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(trie).contains(captor.capture());
        assertThat(captor.getValue()).isEqualTo("\u672a\u77e5\u8a9e");
    }
}

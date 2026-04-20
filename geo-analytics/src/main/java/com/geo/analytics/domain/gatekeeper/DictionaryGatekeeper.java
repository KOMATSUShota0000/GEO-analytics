package com.geo.analytics.domain.gatekeeper;

import com.geo.analytics.domain.support.JapaneseTextNormalizer;
import com.geo.analytics.domain.trie.OffHeapDoubleArrayTrie;
import java.util.Objects;

public final class DictionaryGatekeeper {

    public record GatekeeperResult(boolean dictionaryHit, double confidence, boolean bypassLayer1, String normalizedText) {
    }

    private final OffHeapDoubleArrayTrie trie;

    public DictionaryGatekeeper(OffHeapDoubleArrayTrie trie) {
        this.trie = Objects.requireNonNull(trie);
    }

    public GatekeeperResult evaluate(String rawText) {
        if (rawText == null) {
            return new GatekeeperResult(false, Double.NaN, false, null);
        }
        String normalizedText = JapaneseTextNormalizer.normalizeBrandText(rawText);
        boolean hit = trie.contains(normalizedText);
        if (hit) {
            return new GatekeeperResult(true, 1.0, true, normalizedText);
        }
        return new GatekeeperResult(false, Double.NaN, false, normalizedText);
    }
}

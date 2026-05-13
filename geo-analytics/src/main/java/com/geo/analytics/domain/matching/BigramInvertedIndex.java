package com.geo.analytics.domain.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * HashMap-based inverted index (bigram key → posting doc ids). Replaces legacy open-addressing primitive tables.
 */
public final class BigramInvertedIndex {

    private final Map<String, List<Integer>> postingsByBigram;
    private boolean sealed;

    public BigramInvertedIndex() {
        this.postingsByBigram = new HashMap<>();
    }

    /** @param initialPairCapacityPow2 treated as suggested initial {@link HashMap} capacity (positive). */
    public BigramInvertedIndex(int initialPairCapacityPow2) {
        int cap = initialPairCapacityPow2 > 0 ? initialPairCapacityPow2 : 16;
        this.postingsByBigram = new HashMap<>(cap);
    }

    public void addPosting(String bigramKey, int docId) {
        if (sealed) {
            throw new IllegalStateException();
        }
        postingsByBigram.computeIfAbsent(bigramKey, k -> new ArrayList<>()).add(docId);
    }

    public void seal() {
        if (sealed) {
            return;
        }
        sealed = true;
        for (List<Integer> list : postingsByBigram.values()) {
            Collections.sort(list);
        }
    }

    /** @return non-negative row id placeholder when postings exist (compat with legacy API), else {@code -1}. */
    public int lookupRow(String bigramKey) {
        if (!sealed) {
            throw new IllegalStateException();
        }
        List<Integer> list = postingsByBigram.get(bigramKey);
        return list != null && !list.isEmpty() ? 0 : -1;
    }

    public void forEachPosting(String key, IntConsumer consumer) {
        List<Integer> list = postingsByBigram.get(key);
        if (list == null) {
            return;
        }
        for (Integer id : list) {
            consumer.accept(id);
        }
    }
}

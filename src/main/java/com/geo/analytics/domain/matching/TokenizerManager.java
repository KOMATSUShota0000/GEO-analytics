package com.geo.analytics.domain.matching;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public final class TokenizerManager {

    private final Dictionary dictionary;
    private final Semaphore sudachiSemaphore;
    private final ArrayDeque<Tokenizer> tokenizerPool = new ArrayDeque<>();
    private final ReentrantLock poolLock = new ReentrantLock();

    public TokenizerManager(Dictionary dictionary) {
        this(dictionary, clampProc(Runtime.getRuntime().availableProcessors()));
    }

    public TokenizerManager(Dictionary dictionary, int maxConcurrentSudachi) {
        this.dictionary = Objects.requireNonNull(dictionary);
        this.sudachiSemaphore = new Semaphore(Math.max(2, maxConcurrentSudachi));
    }

    private static int clampProc(int p) {
        return Math.max(2, Math.min(16, p));
    }

    public Dictionary sharedDictionary() {
        return dictionary;
    }

    public <T> T withPerTaskTokenizer(Function<Tokenizer, T> work) {
        sudachiSemaphore.acquireUninterruptibly();
        Tokenizer tokenizer = null;
        poolLock.lock();
        try {
            tokenizer = tokenizerPool.pollFirst();
        } finally {
            poolLock.unlock();
        }
        if (tokenizer == null) {
            tokenizer = dictionary.create();
        }
        try {
            return work.apply(tokenizer);
        } finally {
            poolLock.lock();
            try {
                tokenizerPool.addFirst(tokenizer);
            } finally {
                poolLock.unlock();
                sudachiSemaphore.release();
            }
        }
    }

    public MorphemeList tokenizeSplitC(String text) {
        String t = text == null ? "" : text.strip();
        return withPerTaskTokenizer(tokenizer -> tokenizer.tokenize(Tokenizer.SplitMode.C, t));
    }

    /**
     * Sudachi による形態素分割と正規化形の列（品詞フィルタなし）。事前正規化用途。
     */
    public List<String> tokenizeToNormalizedList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return withPerTaskTokenizer(tokenizer -> {
            MorphemeList list = tokenizer.tokenize(Tokenizer.SplitMode.C, text);
            ArrayList<String> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                String n = normalizedSurface(list.get(i));
                if (!n.isBlank()) {
                    out.add(n);
                }
            }
            return List.copyOf(out);
        });
    }

    private static String normalizedSurface(Morpheme morpheme) {
        String n = morpheme.normalizedForm();
        if (n == null || n.isBlank() || "*".equals(n)) {
            String s = morpheme.surface();
            return s != null ? s.strip() : "";
        }
        return n.strip();
    }
}

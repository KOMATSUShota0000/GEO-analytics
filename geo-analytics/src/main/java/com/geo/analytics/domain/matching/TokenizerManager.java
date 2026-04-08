package com.geo.analytics.domain.matching;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.lang.StrictMath;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class TokenizerManager {

    private static final Set<String> NORMALIZED_CONTENT_POS =
            Set.of("名詞", "動詞", "形容詞", "形状詞", "代名詞", "未知語");

    private final Dictionary dictionary;
    private final Semaphore sudachiSemaphore;
    private final ArrayDeque<Tokenizer> tokenizerPool = new ArrayDeque<>();
    private final ReentrantLock poolLock = new ReentrantLock();

    public TokenizerManager(Dictionary dictionary) {
        this(dictionary, clampProc(Runtime.getRuntime().availableProcessors()));
    }

    public TokenizerManager(Dictionary dictionary, int maxConcurrentSudachi) {
        this.dictionary = Objects.requireNonNull(dictionary);
        this.sudachiSemaphore = new Semaphore(StrictMath.max(2, maxConcurrentSudachi));
    }

    private static int clampProc(int p) {
        return StrictMath.max(2, StrictMath.min(16, p));
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

    public List<String> tokenizeToNormalizedList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return withPerTaskTokenizer(tokenizer -> {
            MorphemeList list = tokenizer.tokenize(Tokenizer.SplitMode.C, text);
            return IntStream.range(0, list.size())
                    .mapToObj(list::get)
                    .filter(m -> {
                        List<String> pos = m.partOfSpeech();
                        return pos != null && !pos.isEmpty() && NORMALIZED_CONTENT_POS.contains(pos.get(0));
                    })
                    .map(Morpheme::normalizedForm)
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
        });
    }
}

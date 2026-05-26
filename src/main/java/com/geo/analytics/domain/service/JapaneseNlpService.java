package com.geo.analytics.domain.service;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.lang.StrictMath;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sudachi による表記ゆれ・読みキー抽出とトークン数カウント。ルールベースの感情やスタッフィング補正は行わない。
 */
public final class JapaneseNlpService {
    private final Dictionary dictionary;
    private final Semaphore sudachiSemaphore;
    private final ArrayDeque<Tokenizer> tokenizerPool = new ArrayDeque<>();
    private final ReentrantLock poolLock = new ReentrantLock();

    public JapaneseNlpService(Dictionary dictionary) {
        this(dictionary, clampProc(Runtime.getRuntime().availableProcessors()));
    }

    public JapaneseNlpService(Dictionary dictionary, int maxConcurrentSudachi) {
        this.dictionary = dictionary;
        this.sudachiSemaphore = new Semaphore(StrictMath.max(2, maxConcurrentSudachi));
    }

    private static int clampProc(int p) {
        return StrictMath.max(2, StrictMath.min(16, p));
    }

    public String normalizedForm(String text) {
        return normalizedKey(text);
    }

    public String readingForm(String text) {
        return readingKey(text);
    }

    public String normalizedKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var morphemes = tokenize(text);
        var builder = new StringBuilder();
        for (var i = 0; i < morphemes.size(); i++) {
            builder.append(safeNormalizedForm(morphemes.get(i)));
        }
        return builder.toString();
    }

    public String readingKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var morphemes = tokenize(text);
        var builder = new StringBuilder();
        for (var i = 0; i < morphemes.size(); i++) {
            builder.append(safeReadingForm(morphemes.get(i)));
        }
        return builder.toString();
    }

    public int totalTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenize(text).size();
    }

    private MorphemeList tokenize(String text) {
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
            return tokenizer.tokenize(Tokenizer.SplitMode.C, text.strip());
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

    private static String safeNormalizedForm(Morpheme morpheme) {
        var n = morpheme.normalizedForm();
        if (n == null || n.isBlank() || "*".equals(n)) {
            var s = morpheme.surface();
            return s != null ? s : "";
        }
        return n;
    }

    private static String safeReadingForm(Morpheme morpheme) {
        var r = morpheme.readingForm();
        return r != null ? r : "";
    }
}

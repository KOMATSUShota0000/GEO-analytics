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

    /**
     * 形態素の境界。{@code boundary} の添字は {@code text.strip()} 後の文字列の位置に対応し、
     * 長さは文字列長 + 1。形態素が始まる位置と終わる位置が true になる。
     */
    public record TokenBoundaries(boolean[] boundary, int tokenCount) {}

    /**
     * Why: ブランド言及を数えるとき、語の途中への誤一致（「ガスト」が「ガストロノミー」の一部に一致する等）を
     * 防ぐには形態素の境界が要る。表記キー（normalizedKey）は境界を失うため、位置情報をそのまま返す。
     */
    public TokenBoundaries tokenBoundaries(String text) {
        if (text == null || text.isBlank()) {
            return new TokenBoundaries(new boolean[1], 0);
        }
        String stripped = text.strip();
        boolean[] boundary = new boolean[stripped.length() + 1];
        var morphemes = tokenize(stripped);
        for (var i = 0; i < morphemes.size(); i++) {
            Morpheme m = morphemes.get(i);
            int begin = m.begin();
            int end = m.end();
            if (begin >= 0 && begin < boundary.length) {
                boundary[begin] = true;
            }
            if (end >= 0 && end < boundary.length) {
                boundary[end] = true;
            }
        }
        return new TokenBoundaries(boundary, morphemes.size());
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

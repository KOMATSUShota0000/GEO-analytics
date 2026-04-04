package com.geo.analytics.domain.service;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public final class JapaneseNlpService {
    private static final String NOUN = "名詞";
    private static final Pattern DOUBLE_NEGATION = Pattern.compile("ないわけではない|なくはない|ないことはない|ぬわけではない");
    private static final Pattern INTENSIFIER = Pattern.compile("非常に|圧倒的に|最も");
    private final Dictionary dictionary;
    private final Semaphore sudachiSemaphore;
    private final ArrayDeque<Tokenizer> tokenizerPool = new ArrayDeque<>();
    private final ReentrantLock poolLock = new ReentrantLock();

    public JapaneseNlpService(Dictionary dictionary) {
        this(dictionary, Math.clamp(Runtime.getRuntime().availableProcessors(), 2, 16));
    }

    public JapaneseNlpService(Dictionary dictionary, int maxConcurrentSudachi) {
        this.dictionary = dictionary;
        this.sudachiSemaphore = new Semaphore(Math.max(2, maxConcurrentSudachi));
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

    public int countNounTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        var morphemes = tokenize(text);
        var n = 0;
        for (var i = 0; i < morphemes.size(); i++) {
            if (isNoun(morphemes.get(i))) {
                n++;
            }
        }
        return n;
    }

    public int totalTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenize(text).size();
    }

    public int countTargetPhraseOccurrences(String text, String phrase) {
        if (text == null || text.isBlank() || phrase == null || phrase.isBlank()) {
            return 0;
        }
        var p = EntityNormalizer.prepareForSudachi(phrase);
        if (p.isBlank()) {
            return 0;
        }
        var haystack = tokenize(text);
        var needle = tokenize(p);
        if (needle.isEmpty()) {
            return 0;
        }
        var c = 0;
        var last = haystack.size() - needle.size();
        for (var i = 0; i <= last; i++) {
            if (matchesAt(haystack, needle, i)) {
                c++;
            }
        }
        return c;
    }

    public double wordDensity(String text, String phrase) {
        var total = totalTokenCount(text);
        if (total == 0) {
            return 0.0;
        }
        return (double) countTargetPhraseOccurrences(text, phrase) / total;
    }

    public double normalizeSentimentCoefficient(String text, double sentimentCoefficient) {
        if (text == null || text.isBlank()) {
            return sentimentCoefficient;
        }
        if (DOUBLE_NEGATION.matcher(text).find()) {
            return 1.0;
        }
        return sentimentCoefficient;
    }

    public double applyIntensifierBoost(String text, double score) {
        if (text == null || text.isBlank()) {
            return score;
        }
        if (INTENSIFIER.matcher(text).find()) {
            return score * 1.2;
        }
        return score;
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

    private static boolean matchesAt(MorphemeList haystack, MorphemeList needle, int start) {
        for (var j = 0; j < needle.size(); j++) {
            if (!Objects.equals(
                safeNormalizedForm(haystack.get(start + j)),
                safeNormalizedForm(needle.get(j)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNoun(Morpheme morpheme) {
        List<String> pos = morpheme.partOfSpeech();
        return pos != null && !pos.isEmpty() && NOUN.equals(pos.getFirst());
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

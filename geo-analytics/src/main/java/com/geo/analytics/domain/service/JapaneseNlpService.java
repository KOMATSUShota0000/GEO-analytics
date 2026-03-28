package com.geo.analytics.domain.service;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.util.List;
import java.util.Objects;

public final class JapaneseNlpService {
    private static final String NOUN = "名詞";
    private final ThreadLocal<Tokenizer> tokenizerHolder;

    public JapaneseNlpService(Dictionary dictionary) {
        this.tokenizerHolder = ThreadLocal.withInitial(dictionary::create);
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

    private MorphemeList tokenize(String text) {
        return tokenizerHolder.get().tokenize(Tokenizer.SplitMode.C, text.strip());
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

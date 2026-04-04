package com.geo.analytics.domain.matching;

import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.util.Objects;

public final class NormalizationLayer {

    private final TokenizerManager tokenizerManager;

    public NormalizationLayer(TokenizerManager tokenizerManager) {
        this.tokenizerManager = Objects.requireNonNull(tokenizerManager);
    }

    public String surfaceForJaroWinkler(String text) {
        String t = text == null ? "" : text.strip();
        return tokenizerManager.withPerTaskTokenizer(tokenizer -> buildNormalizedConcat(tokenizer, t));
    }

    public String surfaceForPhoneticEncoding(String text) {
        String t = text == null ? "" : text.strip();
        return tokenizerManager.withPerTaskTokenizer(tokenizer -> buildReadingConcat(tokenizer, t));
    }

    private static String buildNormalizedConcat(Tokenizer tokenizer, String text) {
        MorphemeList list = tokenizer.tokenize(Tokenizer.SplitMode.C, text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(safeNormalizedForm(list.get(i)));
        }
        return sb.toString();
    }

    private static String buildReadingConcat(Tokenizer tokenizer, String text) {
        MorphemeList list = tokenizer.tokenize(Tokenizer.SplitMode.C, text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(safeReadingForm(list.get(i)));
        }
        return sb.toString();
    }

    private static String safeNormalizedForm(Morpheme morpheme) {
        String n = morpheme.normalizedForm();
        if (n == null || n.isBlank() || "*".equals(n)) {
            String s = morpheme.surface();
            return s != null ? s : "";
        }
        return n;
    }

    private static String safeReadingForm(Morpheme morpheme) {
        String r = morpheme.readingForm();
        if (r == null || r.isBlank() || "*".equals(r)) {
            return safeNormalizedForm(morpheme);
        }
        return r;
    }
}

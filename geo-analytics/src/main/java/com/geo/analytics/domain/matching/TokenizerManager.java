package com.geo.analytics.domain.matching;

import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.MorphemeList;
import com.worksap.nlp.sudachi.Tokenizer;
import java.util.Objects;
import java.util.function.Function;

public final class TokenizerManager {

    private final Dictionary dictionary;
    private final Object dictionaryGate;

    public TokenizerManager(Dictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
        this.dictionaryGate = new byte[0];
    }

    public Dictionary sharedDictionary() {
        return dictionary;
    }

    public Tokenizer newPerTaskTokenizer() {
        synchronized (dictionaryGate) {
            return dictionary.create();
        }
    }

    public <T> T withPerTaskTokenizer(Function<Tokenizer, T> work) {
        Tokenizer tokenizer;
        synchronized (dictionaryGate) {
            tokenizer = dictionary.create();
        }
        try {
            return work.apply(tokenizer);
        } finally {
            if (tokenizer instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                }
            }
        }
    }

    public MorphemeList tokenizeSplitC(String text) {
        String t = text == null ? "" : text.strip();
        return withPerTaskTokenizer(tokenizer -> tokenizer.tokenize(Tokenizer.SplitMode.C, t));
    }
}

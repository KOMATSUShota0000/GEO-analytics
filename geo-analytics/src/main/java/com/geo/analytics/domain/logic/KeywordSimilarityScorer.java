package com.geo.analytics.domain.logic;

import java.lang.StrictMath;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * NFKC 正規化した文字 bigram 集合に対する Jaccard 係数による軽量類似度。
 */
@Component
public final class KeywordSimilarityScorer implements SimilarityScorer {

    private static final int MIN_LENGTH_FOR_BIGRAM = 2;

    @Override
    public double score(String query, String content) {
        String q = normalize(query);
        String c = normalize(content);
        if (q.isEmpty() && c.isEmpty()) {
            return 1.0d;
        }
        if (q.isEmpty() || c.isEmpty()) {
            return 0.0d;
        }
        Set<String> bq = bigrams(q);
        Set<String> bc = bigrams(c);
        if (bq.isEmpty() || bc.isEmpty()) {
            return unigramJaccard(q, c);
        }
        return jaccard(bq, bc);
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (!Character.isWhitespace(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static Set<String> bigrams(String s) {
        Set<String> out = new HashSet<>();
        if (s.length() < MIN_LENGTH_FOR_BIGRAM) {
            return out;
        }
        for (int i = 0; i <= s.length() - MIN_LENGTH_FOR_BIGRAM; i++) {
            out.add(s.substring(i, i + MIN_LENGTH_FOR_BIGRAM));
        }
        return out;
    }

    private static double unigramJaccard(String a, String b) {
        Set<String> ua = unigrams(a);
        Set<String> ub = unigrams(b);
        return jaccard(ua, ub);
    }

    private static Set<String> unigrams(String s) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(String.valueOf(s.charAt(i)));
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0d;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0d;
        }
        int inter = 0;
        for (String x : a) {
            if (b.contains(x)) {
                inter++;
            }
        }
        int uni = a.size() + b.size() - inter;
        if (uni <= 0) {
            return 0.0d;
        }
        return StrictMath.min(1.0d, (double) inter / (double) uni);
    }
}

package com.geo.analytics.application.validator;

import com.geo.analytics.application.dto.DomainAnalysisResult;
import com.geo.analytics.application.dto.SuggestedQuery;
import com.geo.analytics.application.exception.QueryProposalException;
import com.geo.analytics.application.exception.QueryProposalPhase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AI が生成した {@link DomainAnalysisResult} の品質を検証する。不合格時は {@link QueryProposalException}（{@code VALIDATION}）を送出する。
 */
@Component
public class QueryQualityValidator {

    static final int MIN_QUERY_COUNT = 5;

    static final int MAX_QUERY_COUNT = 10;

    static final int MIN_QUERY_CODE_POINTS = 5;

    private static final int MIN_INFERRED_PERSONA_CODE_POINTS = 50;

    /**
     * 正規化 Levenshtein 距離 / max(長さ) がこの値以下なら「過度に類似」とみなす。
     */
    private static final double MAX_NORMALIZED_EDIT_RATIO = 0.15;

    static final double JACCARD_TOO_SIMILAR = 0.70;

    private static final int MIN_TOKEN_SET_FOR_JACCARD = 3;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\p{L}[\\p{L}\\p{M}]*|\\p{N}+");

    private static final List<String> META_PHRASES_LOWER = List.of(
            "以下に",
            "以下が",
            "以下は",
            "10個の質問",
            "10件の質問",
            "10 個の質問",
            "ご要望通り",
            "ご要望どおり",
            "aiとして",
            "language model",
            "large language model",
            "chatgpt",
            "claude",
            "json形式",
            "```json",
            "here are",
            "here is",
            "as an ai",
            "as a language",
            "i cannot",
            "質問を列挙",
            "以下の通り",
            "こちらに",
            "こちらが",
            "こちらは",
            "まとめました",
            "リストにまとめ",
            "一覧にしました",
            "ご提案",
            "ご提案いたします",
            "をご提案",
            "参考までに",
            "念のため",
            "チェックリスト",
            "質問リスト",
            "リストです",
            "列挙",
            "としてお答え",
            "回答として",
            "出力します",
            "出力いたします");

    public void validate(DomainAnalysisResult result) {
        if (result == null) {
            throw new QueryProposalException(
                    QueryProposalPhase.VALIDATION, "Validation failed", "result is null", null);
        }
        List<SuggestedQuery> queries = result.queries();
        int queryCount = queries.size();
        if (queryCount < MIN_QUERY_COUNT || queryCount > MAX_QUERY_COUNT) {
            throw new QueryProposalException(
                    QueryProposalPhase.VALIDATION,
                    "Validation failed",
                    "queryCount must be between " + MIN_QUERY_COUNT + " and " + MAX_QUERY_COUNT + " was " + queryCount,
                    null);
        }

        String personaTrimmed = result.inferredPersona().strip();
        int personaCodePoints = personaTrimmed.codePointCount(0, personaTrimmed.length());
        if (personaCodePoints < MIN_INFERRED_PERSONA_CODE_POINTS) {
            throw new QueryProposalException(
                    QueryProposalPhase.VALIDATION,
                    "Validation failed",
                    "inferredPersona too short (minimum "
                            + MIN_INFERRED_PERSONA_CODE_POINTS
                            + " code points after trim, was "
                            + personaCodePoints
                            + ")",
                    null);
        }
        if (containsMetaPhrase(personaTrimmed)) {
            throw new QueryProposalException(
                    QueryProposalPhase.VALIDATION,
                    "Validation failed",
                    "meta phrase in inferredPersona",
                    null);
        }

        List<String> normalizedQueries = new ArrayList<>(MAX_QUERY_COUNT);
        for (int i = 0; i < queries.size(); i++) {
            SuggestedQuery q = queries.get(i);
            String text = q.queryText();
            if (text.codePointCount(0, text.length()) <= MIN_QUERY_CODE_POINTS) {
                throw new QueryProposalException(
                        QueryProposalPhase.VALIDATION,
                        "Validation failed",
                        "queryText too short at index " + i + " (must exceed " + MIN_QUERY_CODE_POINTS + " code points)",
                        null);
            }
            String intent = q.intent();
            if (intent.isBlank()) {
                throw new QueryProposalException(
                        QueryProposalPhase.VALIDATION, "Validation failed", "empty intent at index " + i, null);
            }
            if (containsMetaPhrase(text) || containsMetaPhrase(intent)) {
                throw new QueryProposalException(
                        QueryProposalPhase.VALIDATION,
                        "Validation failed",
                        "meta phrase in query at index " + i,
                        null);
            }
            String norm = normalize(text);
            for (int j = 0; j < normalizedQueries.size(); j++) {
                String prior = normalizedQueries.get(j);
                if (norm.equals(prior)) {
                    throw new QueryProposalException(
                            QueryProposalPhase.VALIDATION,
                            "Validation failed",
                            "duplicate queryText at indices " + j + " and " + i,
                            null);
                }
                if (areOverlySimilar(norm, prior)) {
                    throw new QueryProposalException(
                            QueryProposalPhase.VALIDATION,
                            "Validation failed",
                            "queries too similar (edit distance) at indices " + j + " and " + i,
                            null);
                }
                Set<String> tokensA = extractTokens(norm);
                Set<String> tokensB = extractTokens(prior);
                if (!skipJaccardForSmallTokenSets(tokensA, tokensB)) {
                    if (jaccard(tokensA, tokensB) >= JACCARD_TOO_SIMILAR) {
                        throw new QueryProposalException(
                                QueryProposalPhase.VALIDATION,
                                "Validation failed",
                                "queries too similar (Jaccard overlap) at indices " + j + " and " + i,
                                null);
                    }
                }
            }
            normalizedQueries.add(norm);
        }
    }

    /**
     * 両セットのサイズがともに {@link #MIN_TOKEN_SET_FOR_JACCARD} 未満なら Jaccard チェックを行わない。
     */
    private static boolean skipJaccardForSmallTokenSets(Set<String> a, Set<String> b) {
        return a.size() < MIN_TOKEN_SET_FOR_JACCARD && b.size() < MIN_TOKEN_SET_FOR_JACCARD;
    }

    /** 正規化済み質問本文から連続字母・結合記号および数字連結を抽出する。 */
    static Set<String> extractTokens(String normalizedText) {
        Set<String> tokens = new HashSet<>();
        String s = normalizedText.toLowerCase(Locale.ROOT);
        Matcher m = TOKEN_PATTERN.matcher(s);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String t : a) {
            if (b.contains(t)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        if (union == 0) {
            return 0.0;
        }
        return (double) intersection / union;
    }

    private static boolean containsMetaPhrase(String text) {
        String n = normalize(text).toLowerCase(Locale.ROOT);
        for (String phrase : META_PHRASES_LOWER) {
            if (n.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        String nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return nfkc.replaceAll("\\s+", " ").trim();
    }

    static boolean areOverlySimilar(String a, String b) {
        int[] ac = a.codePoints().toArray();
        int[] bc = b.codePoints().toArray();
        int maxLen = Math.max(ac.length, bc.length);
        if (maxLen == 0) {
            return false;
        }
        int d = levenshtein(ac, bc);
        return (d / (double) maxLen) <= MAX_NORMALIZED_EDIT_RATIO;
    }

    private static int levenshtein(int[] a, int[] b) {
        int n = a.length;
        int m = b.length;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}

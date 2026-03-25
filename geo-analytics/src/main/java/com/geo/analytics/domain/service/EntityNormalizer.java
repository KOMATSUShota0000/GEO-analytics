package com.geo.analytics.domain.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class EntityNormalizer {
    public static final String UNMATCHED = "OTHERS";
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern JP_SUFFIX = Pattern.compile(
        "(株式会社|（株）|\\(株\\)|有限会社|合同会社|合名会社|合資会社|一般社団法人|一般財団法人)\\s*$");
    private static final Pattern EN_SUFFIX = Pattern.compile(
        "(?i),?\\s*(Inc\\.?|LLC|Ltd\\.?|Limited|Corp\\.?|Corporation|Co\\.,?|GmbH|S\\.\\s*A\\.?)\\s*$");
    private EntityNormalizer() {
    }
    public static String hostLabelFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String u = url.trim();
            if (!u.contains("://")) {
                u = "https://" + u;
            }
            URI uri = URI.create(u);
            String host = uri.getHost();
            return host != null ? host : "";
        } catch (Exception e) {
            return url.trim();
        }
    }
    public static String resolve(String rawName, String mainBrand, List<String> competitorBrands, boolean isProPlan) {
        String raw = rawName == null ? "" : rawName;
        String main = mainBrand == null ? "" : mainBrand;
        List<String> comps = competitorBrands == null ? List.of() : competitorBrands;
        String nr = normalizeForCompare(raw);
        if (nr.isEmpty()) {
            return UNMATCHED;
        }
        List<NameCandidate> cands = new ArrayList<>();
        if (!main.isBlank()) {
            cands.add(new NameCandidate(main, normalizeForCompare(main)));
        }
        for (String c : comps) {
            if (c != null && !c.isBlank()) {
                cands.add(new NameCandidate(c, normalizeForCompare(c)));
            }
        }
        for (NameCandidate cand : cands) {
            if (nr.equals(cand.norm)) {
                return cand.canonical;
            }
        }
        for (NameCandidate cand : cands) {
            if (cand.norm.isEmpty()) {
                continue;
            }
            if (containsMatch(nr, cand.norm)) {
                return cand.canonical;
            }
        }
        if (isProPlan) {
            for (NameCandidate cand : cands) {
                if (cand.norm.isEmpty()) {
                    continue;
                }
                double sim = similarityLevenshtein(nr, cand.norm);
                if (sim >= 0.85) {
                    return cand.canonical;
                }
            }
        }
        return UNMATCHED;
    }
    private static String normalizeForCompare(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC);
        n = stripCorporateSuffixes(n);
        n = WS.matcher(n).replaceAll("");
        return n.toLowerCase(Locale.ROOT);
    }
    private static String stripCorporateSuffixes(String s) {
        String t = s.trim();
        for (int i = 0; i < 8; i++) {
            String u = JP_SUFFIX.matcher(t).replaceFirst("").trim();
            u = EN_SUFFIX.matcher(u).replaceFirst("").trim();
            if (u.equals(t)) {
                break;
            }
            t = u;
        }
        return t;
    }
    private static boolean containsMatch(String a, String b) {
        int la = a.length();
        int lb = b.length();
        if (la < 2 || lb < 2) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }
    private static double similarityLevenshtein(String s1, String s2) {
        int[] c1 = s1.codePoints().toArray();
        int[] c2 = s2.codePoints().toArray();
        int m = c1.length;
        int n = c2.length;
        int maxLen = Math.max(m, n);
        if (maxLen == 0) {
            return 1.0;
        }
        int dist = levenshteinDp(c1, c2);
        return 1.0 - dist / (double) maxLen;
    }
    private static int levenshteinDp(int[] a, int[] b) {
        int m = a.length;
        int n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                int v = dp[i - 1][j] + 1;
                if (dp[i][j - 1] + 1 < v) {
                    v = dp[i][j - 1] + 1;
                }
                if (dp[i - 1][j - 1] + cost < v) {
                    v = dp[i - 1][j - 1] + cost;
                }
                dp[i][j] = v;
            }
        }
        return dp[m][n];
    }
    private record NameCandidate(String canonical, String norm) {
    }
}

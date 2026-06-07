package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.DiscoveredLink;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class CorePageLinkScorer {

    // 追従するサブページ数の上限。ハブ型サイト（トップが薄く実コンテンツがサブに分散）の
    // 取りこぼしを減らすため、重要度上位5ページまで追従する（入口と合わせ最大6ページ）。
    public static final int MAX_FOLLOW = 5;

    private static final int HIGH_WEIGHT = 80;

    private static final int MEDIUM_WEIGHT = 45;

    private static final String[] HIGH_TOKENS = {
        "/price", "/plan", "料金", "/company", "/about", "会社", "/faq", "/service", "サービス"
    };

    private static final String[] MEDIUM_TOKENS = {"/contact", "お問い合わせ"};

    private CorePageLinkScorer() {}

    public static List<String> selectTopFollowUrls(List<DiscoveredLink> candidates, String canonicalEntryUrl) {
        if (canonicalEntryUrl == null || canonicalEntryUrl.isBlank()) {
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        URI entryUri = parseHttpUri(canonicalEntryUrl.trim());
        if (entryUri == null || entryUri.getHost() == null) {
            return List.of();
        }
        String entryHostNorm = canonicalHost(entryUri.getHost());
        String entryKey = canonicalUrlKey(entryUri.normalize());
        LinkedHashMap<String, Integer> bestScoreByKey = new LinkedHashMap<>();
        LinkedHashMap<String, String> bestUrlByKey = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            DiscoveredLink dl = candidates.get(i);
            if (dl == null || dl.absoluteUrl() == null || dl.absoluteUrl().isBlank()) {
                continue;
            }
            String rawAbs = dl.absoluteUrl().trim();
            if (blockedSchemePrefix(rawAbs)) {
                continue;
            }
            URI u = parseHttpUri(rawAbs);
            if (u == null || u.getScheme() == null || u.getHost() == null) {
                continue;
            }
            String scheme = u.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                continue;
            }
            if (!entryHostNorm.equals(canonicalHost(u.getHost()))) {
                continue;
            }
            URI normalized = u.normalize();
            String rawPath = normalized.getRawPath() == null ? "" : normalized.getRawPath().toLowerCase(Locale.ROOT);
            String np = dl.normalizedPath() == null ? "" : dl.normalizedPath().toLowerCase(Locale.ROOT);
            String pathProbe = rawPath.length() >= np.length() ? rawPath : np;
            if (hasBlockedAssetExtension(pathProbe) || hasBlockedAssetExtension(rawPath) || hasBlockedAssetExtension(np)) {
                continue;
            }
            String rq = normalized.getRawQuery();
            String pathWithQuery =
                    rq == null || rq.isBlank()
                            ? rawPath
                            : rawPath + "?" + rq.toLowerCase(Locale.ROOT);
            String anchorLow = dl.anchorText() == null ? "" : dl.anchorText().toLowerCase(Locale.ROOT);
            String haystack = pathWithQuery + " " + np + " " + anchorLow;
            if (!passesStructuralGate(normalized, haystack, rawAbs)) {
                continue;
            }
            int score = accumulateScore(haystack);
            if (score <= 0) {
                continue;
            }
            String key = canonicalUrlKey(normalized);
            if (key.equals(entryKey)) {
                continue;
            }
            String fetchUrl = normalized.toString();
            Integer prior = bestScoreByKey.get(key);
            if (prior == null || score > prior) {
                bestScoreByKey.put(key, score);
                bestUrlByKey.put(key, fetchUrl);
            }
        }
        if (bestScoreByKey.isEmpty()) {
            return List.of();
        }
        ArrayList<String> keys = new ArrayList<>(bestScoreByKey.keySet());
        keys.sort((a, b) -> {
            int sa = bestScoreByKey.getOrDefault(a, 0);
            int sb = bestScoreByKey.getOrDefault(b, 0);
            if (sa != sb) {
                return Integer.compare(sb, sa);
            }
            return a.compareTo(b);
        });
        ArrayList<String> out = new ArrayList<>(StrictMath.min(MAX_FOLLOW, keys.size()));
        for (int i = 0; i < keys.size() && out.size() < MAX_FOLLOW; i++) {
            String u = bestUrlByKey.get(keys.get(i));
            if (u != null && !u.isBlank()) {
                out.add(u);
            }
        }
        return List.copyOf(out);
    }

    private static boolean passesStructuralGate(URI normalized, String haystackLower, String rawAbsLowerSource) {
        String abs = rawAbsLowerSource.toLowerCase(Locale.ROOT);
        if (blockedSchemePrefix(abs)) {
            return false;
        }
        if (haystackLower.contains(".pdf")) {
            return false;
        }
        String p = normalized.getRawPath() == null ? "" : normalized.getRawPath().toLowerCase(Locale.ROOT);
        return !hasBlockedAssetExtension(p);
    }

    private static boolean blockedSchemePrefix(String rawTrimmed) {
        String lower = rawTrimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("javascript:")
                || lower.startsWith("data:");
    }

    private static boolean hasBlockedAssetExtension(String pathLower) {
        return pathLower.endsWith(".pdf")
                || pathLower.endsWith(".png")
                || pathLower.endsWith(".jpg")
                || pathLower.endsWith(".jpeg")
                || pathLower.endsWith(".gif")
                || pathLower.endsWith(".webp")
                || pathLower.endsWith(".svg")
                || pathLower.endsWith(".ico")
                || pathLower.endsWith(".css")
                || pathLower.endsWith(".js")
                || pathLower.endsWith(".woff")
                || pathLower.endsWith(".woff2")
                || pathLower.endsWith(".ttf")
                || pathLower.endsWith(".zip");
    }

    private static int accumulateScore(String haystackLower) {
        int sum = 0;
        for (int i = 0; i < HIGH_TOKENS.length; i++) {
            String t = HIGH_TOKENS[i].toLowerCase(Locale.ROOT);
            if (haystackLower.contains(t)) {
                sum += HIGH_WEIGHT;
            }
        }
        for (int i = 0; i < MEDIUM_TOKENS.length; i++) {
            String t = MEDIUM_TOKENS[i].toLowerCase(Locale.ROOT);
            if (haystackLower.contains(t)) {
                sum += MEDIUM_WEIGHT;
            }
        }
        return sum;
    }

    private static URI parseHttpUri(String url) {
        try {
            return URI.create(url);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String canonicalHost(String host) {
        if (host == null) {
            return "";
        }
        String lower = host.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("www.")) {
            return lower.substring(4);
        }
        return lower;
    }

    private static String canonicalUrlKey(URI u) {
        if (u == null) {
            return "";
        }
        URI normalized = u.normalize();
        String scheme = normalized.getScheme();
        String hostNorm = canonicalHost(normalized.getHost());
        String path = normalized.getRawPath() == null ? "" : normalized.getRawPath();
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String query = normalized.getRawQuery();
        String qpart = query == null || query.isBlank() ? "" : "?" + query;
        if (scheme == null) {
            return "";
        }
        return scheme.toLowerCase(Locale.ROOT) + "://" + hostNorm + path + qpart;
    }
}

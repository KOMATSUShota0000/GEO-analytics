package com.geo.analytics.domain.service;

import com.geo.analytics.domain.matching.ZeroAllocationTokenizer;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public final class EntityNormalizer {
    public static final String UNMATCHED = "OTHERS";
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern JP_SUFFIX = Pattern.compile(
        "(株式会社|（株）|\\(株\\)|有限会社|合同会社|合名会社|合資会社|一般社団法人|一般財団法人)\\s*$");
    private static final Pattern EN_SUFFIX = Pattern.compile(
        "(?i),?\\s*(Inc\\.?|LLC|Ltd\\.?|Limited|Corp\\.?|Corporation|Co\\.,?|GmbH|S\\.\\s*A\\.?)\\s*$");
    private static final double LEXICAL_DICE_THRESHOLD = 0.85;
    private final JapaneseNlpService japaneseNlpService;

    public EntityNormalizer(JapaneseNlpService japaneseNlpService) {
        this.japaneseNlpService = japaneseNlpService;
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

    @SuppressWarnings("unused")
    public String resolve(String rawName, String mainBrand, List<String> competitorBrands, boolean isProPlan) {
        String raw = rawName == null ? "" : rawName;
        String main = mainBrand == null ? "" : mainBrand;
        List<String> comps = competitorBrands == null ? List.of() : competitorBrands;
        String strippedRaw = prepareForSudachi(raw);
        if (strippedRaw.isBlank()) {
            return UNMATCHED;
        }
        String rawNorm = japaneseNlpService.normalizedKey(strippedRaw);
        if (rawNorm.isBlank()) {
            return UNMATCHED;
        }
        String rawReading = japaneseNlpService.readingKey(strippedRaw);
        List<NameCandidate> cands = new ArrayList<>();
        if (!main.isBlank()) {
            String sm = prepareForSudachi(main);
            cands.add(new NameCandidate(
                main,
                japaneseNlpService.normalizedKey(sm),
                japaneseNlpService.readingKey(sm)));
        }
        for (String c : comps) {
            if (c != null && !c.isBlank()) {
                String sc = prepareForSudachi(c);
                cands.add(new NameCandidate(
                    c,
                    japaneseNlpService.normalizedKey(sc),
                    japaneseNlpService.readingKey(sc)));
            }
        }
        for (NameCandidate cand : cands) {
            if (!cand.normSudachi.isBlank() && rawNorm.equals(cand.normSudachi)) {
                return cand.canonical;
            }
        }
        for (NameCandidate cand : cands) {
            if (!rawReading.isBlank() && !cand.readingKey.isBlank() && rawReading.equals(cand.readingKey)) {
                return cand.canonical;
            }
        }
        int[] wA = new int[ZeroAllocationTokenizer.MAX_BIGRAMS_CAP];
        int[] wB = new int[ZeroAllocationTokenizer.MAX_BIGRAMS_CAP];
        for (NameCandidate cand : cands) {
            if (cand.normSudachi.isBlank()) {
                continue;
            }
            if (ZeroAllocationTokenizer.diceCoefficient(rawNorm, cand.normSudachi, wA, wB) >= LEXICAL_DICE_THRESHOLD) {
                return cand.canonical;
            }
        }
        return UNMATCHED;
    }

    public static String prepareForSudachi(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC);
        n = stripCorporateSuffixes(n);
        n = WS.matcher(n).replaceAll("");
        return n.toLowerCase(Locale.ROOT);
    }

    public static String stripCorporateSuffixes(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
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

    private record NameCandidate(String canonical, String normSudachi, String readingKey) {
    }
}

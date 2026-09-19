package com.geo.analytics.domain.service;

import com.geo.analytics.domain.matching.StringBigramTokenizer;
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

    /**
     * 自社ブランドとの同定。一致しなければ {@link #UNMATCHED}。
     *
     * <p>Why: 未使用の {@code isProPlan} 引数を撤去した（#65）。プランで名寄せの規則を変える設計意図は
     * 実装に存在せず、引数だけが残っていた（`.cursorrules` 10節）。
     */
    public String resolve(String rawName, String mainBrand) {
        String raw = rawName == null ? "" : rawName;
        String main = mainBrand == null ? "" : mainBrand;
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
        for (NameCandidate cand : cands) {
            if (cand.normSudachi.isBlank()) {
                continue;
            }
            if (StringBigramTokenizer.diceCoefficient(rawNorm, cand.normSudachi) >= LEXICAL_DICE_THRESHOLD) {
                return cand.canonical;
            }
        }
        return UNMATCHED;
    }

    /**
     * 候補群の中から同じエンティティを探す。見つからなければ {@link #UNMATCHED}。
     *
     * <p>Why: 名寄せエンジンは3段構え（正規化形の一致／読みの一致／バイグラム類似）で完成していたのに、
     * 候補として渡されるのが自社ブランド1件だけで、競合は全件 OTHERS へ潰れていた（#65）。候補を渡せる
     * 入口を用意し、「マネーフォワード」と「Money Forward」のような表記ゆれを1つへ寄せる。
     */
    public String resolveAmong(String rawName, List<String> candidates) {
        String strippedRaw = prepareForSudachi(rawName == null ? "" : rawName);
        if (strippedRaw.isBlank() || candidates == null || candidates.isEmpty()) {
            return UNMATCHED;
        }
        String rawNorm = japaneseNlpService.normalizedKey(strippedRaw);
        if (rawNorm.isBlank()) {
            return UNMATCHED;
        }
        String rawReading = japaneseNlpService.readingKey(strippedRaw);
        List<NameCandidate> cands = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String prepared = prepareForSudachi(candidate);
            cands.add(new NameCandidate(
                    candidate,
                    japaneseNlpService.normalizedKey(prepared),
                    japaneseNlpService.readingKey(prepared)));
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
        for (NameCandidate cand : cands) {
            if (!cand.normSudachi.isBlank()
                    && StringBigramTokenizer.diceCoefficient(rawNorm, cand.normSudachi) >= LEXICAL_DICE_THRESHOLD) {
                return cand.canonical;
            }
        }
        return UNMATCHED;
    }

    /**
     * 「その他」のような残余カテゴリかどうか。
     *
     * <p>Why: LLM は競合の一覧に「その他」を混ぜてくることがある。実在のエンティティではないため、
     * 分子にも分母にも入れない（#65 / オーナー確定 2026-09-19）。
     */
    public static boolean isResidualCategory(String rawName) {
        if (rawName == null) {
            return false;
        }
        String normalized = Normalizer.normalize(rawName.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.equals("その他")
                || normalized.equals("そのほか")
                || normalized.equals("他")
                || normalized.equals("others")
                || normalized.equals("other");
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

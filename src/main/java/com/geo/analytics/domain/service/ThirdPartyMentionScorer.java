package com.geo.analytics.domain.service;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 権威・エンティティ認知（V13_GEO4AXIS の④軸）の中核スコアを算出する純粋ロジック。
 *
 * <p>GEO の本質に基づき「自社サイト以外の独立した第三者ドメインでブランドが言及されている広がり」を
 * 0-20 点へスケールする。多くの独立ソースで言及される実体ほど生成AIに信頼・引用されやすい、という
 * 因果（外部メディア参照 = AI からの信頼）を点数化する。SerpAPI 等で取得した言及URL群を入力に取る。
 */
public final class ThirdPartyMentionScorer {

    /** 権威軸の中核（第三者言及）配点上限。Wikipedia/KG ボーナス（別途）と合算して④軸 0-30 を構成する。 */
    public static final double MAX_AUTHORITY_CORE = 20.0d;

    /**
     * この独立ドメイン数に達すると中核スコアが飽和する（=満点）。件数の素な多寡に振り回されないための
     * 飽和点であり、実データでの絶対値チューニング対象（スコア絶対値チューニングと同根）。
     */
    private static final double DISTINCT_DOMAIN_SATURATION = 8.0d;

    private ThirdPartyMentionScorer() {}

    /**
     * 第三者言及URL群から、自社ドメインを除いた独立ドメイン数を数え、0-{@link #MAX_AUTHORITY_CORE} へスケールする。
     *
     * @param mentionUrls ブランドが言及されていたページのURL群（SerpAPIの organic_results 等）
     * @param selfUrl     解析対象（自社）のURL。これと同一ホスト・サブドメインは第三者から除外する。
     */
    public static double scoreFromMentionUrls(Collection<String> mentionUrls, String selfUrl) {
        return MAX_AUTHORITY_CORE * coverageRatio(distinctThirdPartyDomainCount(mentionUrls, selfUrl));
    }

    /**
     * 自社ドメインを除いた独立した第三者ドメインの数を返す。
     * eTLD+1 の厳密判定は公開サフィックスリスト（外部依存）が必要なため、正規化ホスト（先頭 www. を除去）の
     * distinct 件数で近似する。サブドメイン差は別ドメイン扱いになり得るが、外部ライブラリ非依存を優先する。
     */
    public static int distinctThirdPartyDomainCount(Collection<String> mentionUrls, String selfUrl) {
        if (mentionUrls == null || mentionUrls.isEmpty()) {
            return 0;
        }
        String selfHost = normalizedHost(selfUrl);
        Set<String> domains = new HashSet<>();
        for (String url : mentionUrls) {
            String host = normalizedHost(url);
            if (host.isEmpty()) {
                continue;
            }
            if (!selfHost.isEmpty() && isSameOrSubdomainOf(host, selfHost)) {
                continue;
            }
            domains.add(host);
        }
        return domains.size();
    }

    private static double coverageRatio(int distinctDomainCount) {
        if (distinctDomainCount <= 0) {
            return 0.0d;
        }
        double ratio = distinctDomainCount / DISTINCT_DOMAIN_SATURATION;
        return ratio < 1.0d ? ratio : 1.0d;
    }

    /** URL からホストを取り出し、小文字化＋先頭 www. を除去して正規化する。取り出せなければ空文字。 */
    private static String normalizedHost(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String candidate = url.strip();
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }
        String host;
        try {
            host = URI.create(candidate).getHost();
        } catch (IllegalArgumentException illegalArgumentException) {
            return "";
        }
        if (host == null || host.isBlank()) {
            return "";
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    private static boolean isSameOrSubdomainOf(String host, String baseHost) {
        return host.equals(baseHost) || host.endsWith("." + baseHost);
    }
}

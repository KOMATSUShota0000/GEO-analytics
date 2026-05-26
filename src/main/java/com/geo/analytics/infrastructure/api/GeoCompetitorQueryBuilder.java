package com.geo.analytics.infrastructure.api;

import com.geo.analytics.domain.support.TextWhitespaceNormalizer;

/**
 * Builds the {@code q} parameter for SerpApi / Google web search from brand + user keyword.
 * Uses a single space-separated query (Google の仕様に合わせる)。AND 演算子は日本語クエリで過剰制約になりやすいため使わない。
 */
public final class GeoCompetitorQueryBuilder {

    private GeoCompetitorQueryBuilder() {
    }

    /**
     * @param brandName   job / project brand (e.g. ハーゲンダッツ)
     * @param userQuery   registered keyword line (e.g. アイス)
     * @return UTF-8 安全なプレーン文字列（URL エンコードは HTTP クライアント側で実施）
     */
    public static String build(String brandName, String userQuery) {
        String brand = TextWhitespaceNormalizer.normalize(brandName);
        String kw = TextWhitespaceNormalizer.normalize(userQuery);
        if (brand == null || brand.isBlank()) {
            return kw != null ? kw : "";
        }
        if (kw == null || kw.isBlank()) {
            return brand;
        }
        if (kw.contains(brand)) {
            return kw;
        }
        if (brand.contains(kw)) {
            return brand;
        }
        return brand + " " + kw;
    }
}

package com.geo.analytics.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Serp 等のオーガニック検索結果1行。{@link com.geo.analytics.domain.logic.GeoEvidenceRanker} の入力。
 */
public record GeoEvidenceRow(
        String url,
        String title,
        String snippet,
        Optional<Instant> publishedAt,
        Optional<String> rowRelevanceCategory) {

    public GeoEvidenceRow {
        url = url == null ? "" : url;
        title = title == null ? "" : title;
        snippet = snippet == null ? "" : snippet;
        publishedAt = publishedAt == null ? Optional.empty() : publishedAt;
        rowRelevanceCategory = rowRelevanceCategory == null ? Optional.empty() : rowRelevanceCategory;
    }
}

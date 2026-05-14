package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawledPageData(
    String url,
    String mainContent,
    String contentHash,
    @JsonProperty("seo_technical_evidence_summary") String seoTechnicalEvidenceSummary,
    Map<String, String> metaTags,
    @JsonProperty("has_at_least_one_h1") Boolean hasAtLeastOneH1,
    @JsonProperty("has_at_least_one_h2") Boolean hasAtLeastOneH2
) {
    public CrawledPageData {
        seoTechnicalEvidenceSummary =
                seoTechnicalEvidenceSummary == null ? "" : seoTechnicalEvidenceSummary.strip();
        metaTags = metaTags == null ? Map.of() : Map.copyOf(metaTags);
    }

    public String content() {
        return mainContent;
    }

    /** Schema.org JSON-LD が実装されているか（サマリ文言ベース）。 */
    public boolean hasJsonLdSignal() {
        return seoTechnicalEvidenceSummary.contains("Schema.org: 実装あり");
    }

    public boolean headingHierarchyOk() {
        return Boolean.TRUE.equals(hasAtLeastOneH1) && Boolean.TRUE.equals(hasAtLeastOneH2);
    }
}

package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawledPageData(
    String url,
    String mainContent,
    String contentHash,
    List<String> schemaOrg,
    Map<String, String> metaTags,
    @JsonProperty("has_at_least_one_h1") Boolean hasAtLeastOneH1,
    @JsonProperty("has_at_least_one_h2") Boolean hasAtLeastOneH2
) {
    public CrawledPageData {
        schemaOrg = schemaOrg == null ? List.of() : List.copyOf(schemaOrg);
        metaTags = metaTags == null ? Map.of() : Map.copyOf(metaTags);
    }

    public String content() {
        return mainContent;
    }

    public boolean hasJsonLdSignal() {
        return !schemaOrg.isEmpty();
    }

    public boolean headingHierarchyOk() {
        return Boolean.TRUE.equals(hasAtLeastOneH1) && Boolean.TRUE.equals(hasAtLeastOneH2);
    }
}

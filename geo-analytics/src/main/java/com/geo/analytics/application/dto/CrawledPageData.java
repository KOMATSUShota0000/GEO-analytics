package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrawledPageData(
    String url,
    String mainContent,
    String contentHash,
    List<String> schemaOrg,
    Map<String, String> metaTags
) {
    public CrawledPageData {
        schemaOrg = schemaOrg == null ? List.of() : List.copyOf(schemaOrg);
        metaTags = metaTags == null ? Map.of() : Map.copyOf(metaTags);
    }

    public String content() {
        return mainContent;
    }
}

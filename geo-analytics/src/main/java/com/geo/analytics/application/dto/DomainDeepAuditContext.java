package com.geo.analytics.application.dto;

import java.util.List;

public record DomainDeepAuditContext(
        List<SmartCrawlPage> pages,
        String mergedAuditText,
        String canonicalEntryUrl) {
    public static final int MERGED_TOTAL_MAX_CHARS = 15_000;

    public SmartCrawlPage primaryPage() {
        return pages.getFirst();
    }
}

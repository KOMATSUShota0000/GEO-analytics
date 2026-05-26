package com.geo.analytics.infrastructure.crawler;

public final class PageContentNormalizer {
    private PageContentNormalizer() {
    }

    public static String normalizeVisibleText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String collapsedLines = raw.replaceAll("\\R+", " ");
        return collapsedLines.replaceAll("\\s+", " ").trim();
    }
}

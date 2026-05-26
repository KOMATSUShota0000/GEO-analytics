package com.geo.analytics.domain.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationValidator {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[引用[:：]\\s*([^\\]]{1,})\\]");

    private CitationValidator() {}

    public static boolean hasValidCitation(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return CITATION_PATTERN.matcher(text).find();
    }

    public static List<String> extractCitations(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher m = CITATION_PATTERN.matcher(text);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }
}

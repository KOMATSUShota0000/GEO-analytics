package com.geo.analytics.infrastructure.util;

import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * HTML の {@link Document} からノイズを除き、LLM 向けプレーンテキストへ変換する。ネットワーク I/O は行わない。
 */
@Component
public class HtmlSanitizer {

    private static final int MAX_OUTPUT_CODE_POINTS = 5_000;

    private static final String TRUNCATION_SUFFIX = "（truncated...）";

    private static final Pattern LINE_BREAK_RUN = Pattern.compile("(?:\\R)+");

    private static final Pattern HORIZONTAL_SPACE_RUN = Pattern.compile("[ \t\u000B\f]+");

    public String sanitize(Document doc) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must not be null");
        }
        Document work = doc.clone();
        work.select("script, style, nav, footer, header, aside, iframe, svg, noscript").remove();

        String title = "";
        Element titleEl = work.selectFirst("title");
        if (titleEl != null) {
            title = titleEl.text().trim();
        }
        String description = "";
        Element metaDesc = work.selectFirst("meta[name=description i]");
        if (metaDesc != null) {
            description = metaDesc.attr("content").trim();
        }

        String prefix = "Title: " + title + " / Description: " + description;
        String bodyText = "";
        Element body = work.body();
        if (body != null) {
            bodyText = body.text();
        }
        String combined = prefix + "\n" + bodyText;
        String normalized = normalizeWhitespace(combined);
        return truncateToMaxCodePoints(normalized);
    }

    /**
     * 連続改行（いずれの形式も）を単一の {@code \n} に、行内の連続空白を単一スペースに圧縮する。
     */
    private static String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String withSingleNl = LINE_BREAK_RUN.matcher(text).replaceAll("\n");
        String[] lines = withSingleNl.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String collapsed = HORIZONTAL_SPACE_RUN.matcher(line).replaceAll(" ").trim();
            if (!collapsed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(collapsed);
            }
        }
        return sb.toString();
    }

    private static String truncateToMaxCodePoints(String full) {
        int suffixLen = TRUNCATION_SUFFIX.length();
        int suffixCp = TRUNCATION_SUFFIX.codePointCount(0, suffixLen);
        int totalCp = full.codePointCount(0, full.length());
        if (totalCp <= MAX_OUTPUT_CODE_POINTS) {
            return full;
        }
        int maxContentCp = MAX_OUTPUT_CODE_POINTS - suffixCp;
        if (maxContentCp <= 0) {
            return TRUNCATION_SUFFIX.substring(0, TRUNCATION_SUFFIX.offsetByCodePoints(0, MAX_OUTPUT_CODE_POINTS));
        }
        int end = full.offsetByCodePoints(0, maxContentCp);
        return full.substring(0, end) + TRUNCATION_SUFFIX;
    }
}

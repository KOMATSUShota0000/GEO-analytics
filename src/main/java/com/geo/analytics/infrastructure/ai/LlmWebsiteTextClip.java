package com.geo.analytics.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LlmWebsiteTextClip {
    public static final int MAX_WEBSITE_TEXT_CHARS = 100000;
    private static final Logger log = LoggerFactory.getLogger(LlmWebsiteTextClip.class);

    private LlmWebsiteTextClip() {}

    public static String clipWebsiteText(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_WEBSITE_TEXT_CHARS) {
            return text;
        }
        log.warn("llm_website_text_clipped originalLength={} limit={}", text.length(), MAX_WEBSITE_TEXT_CHARS);
        return text.substring(0, MAX_WEBSITE_TEXT_CHARS);
    }
}

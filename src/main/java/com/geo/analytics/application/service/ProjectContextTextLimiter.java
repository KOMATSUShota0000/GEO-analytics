package com.geo.analytics.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProjectContextTextLimiter {
    private static final int MAX_CODE_POINTS = 1_000;
    private static final Logger log = LoggerFactory.getLogger(ProjectContextTextLimiter.class);

    public String limit(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty()) {
            return text;
        }
        long n = text.codePoints().count();
        if (n <= MAX_CODE_POINTS) {
            return text;
        }
        log.warn("文字数制限を超過したため切り詰めました");
        int[] codePoints = text.codePoints().limit(MAX_CODE_POINTS).toArray();
        return new String(codePoints, 0, codePoints.length);
    }
}

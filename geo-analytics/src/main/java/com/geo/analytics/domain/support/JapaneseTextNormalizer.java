package com.geo.analytics.domain.support;

import java.text.Normalizer;

public final class JapaneseTextNormalizer {

    private static final String[] LEGAL_PATTERNS = {
            "Co., Ltd.",
            "Corp.",
            "株式会社",
            "有限会社",
            "合同会社",
            "Inc.",
            "Ltd.",
            "(株)",
            "(有)"
    };

    private static final String[] BRAND_KEYS = {
            "ビックカメラ",
            "フジフィルム",
            "キャノン"
    };

    private static final String[] BRAND_VALUES = {
            "ビツクカメラ",
            "富士フイルム",
            "キヤノン"
    };

    private JapaneseTextNormalizer() {
    }

    public static String normalizeBrandText(String input) {
        if (input == null) {
            return null;
        }
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        StringBuilder buffer = new StringBuilder(nfkc.length() + 16);
        buffer.append(nfkc);
        for (int iteration = 0; iteration < 3; iteration++) {
            if (!stripLegalAffixesOnce(buffer)) {
                break;
            }
        }
        applyBrandAliases(buffer);
        return buffer.toString();
    }

    private static boolean stripLegalAffixesOnce(StringBuilder buffer) {
        boolean changed = false;
        int i = 0;
        while (i < buffer.length()) {
            boolean matched = false;
            for (String pattern : LEGAL_PATTERNS) {
                int patternLength = pattern.length();
                if (i + patternLength <= buffer.length() && regionMatches(buffer, i, pattern)) {
                    buffer.delete(i, i + patternLength);
                    changed = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                i++;
            }
        }
        return changed;
    }

    private static void applyBrandAliases(StringBuilder buffer) {
        for (int index = 0; index < BRAND_KEYS.length; index++) {
            replaceAll(buffer, BRAND_KEYS[index], BRAND_VALUES[index]);
        }
    }

    private static void replaceAll(StringBuilder buffer, String from, String to) {
        int fromLength = from.length();
        if (fromLength == 0) {
            return;
        }
        int toLength = to.length();
        int i = 0;
        while (i <= buffer.length() - fromLength) {
            if (regionMatches(buffer, i, from)) {
                buffer.replace(i, i + fromLength, to);
                i += toLength;
            } else {
                i++;
            }
        }
    }

    private static boolean regionMatches(StringBuilder buffer, int start, String pattern) {
        int patternLength = pattern.length();
        for (int offset = 0; offset < patternLength; offset++) {
            if (buffer.charAt(start + offset) != pattern.charAt(offset)) {
                return false;
            }
        }
        return true;
    }
}

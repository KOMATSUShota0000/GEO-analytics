package com.geo.analytics.domain.support;

import java.util.regex.Pattern;

/**
 * Normalizes user-entered brand names and search keywords before persistence and analysis.
 * <ol>
 *   <li>{@link String#strip()} — leading/trailing Unicode whitespace</li>
 *   <li>{@code U+3000} (IDEOGRAPHIC SPACE) → {@code U+0020}</li>
 *   <li>Runs of whitespace → single ASCII space ({@code UNICODE_CHARACTER_CLASS} for {@code \s})</li>
 * </ol>
 */
public final class TextWhitespaceNormalizer {

    private static final Pattern CONSECUTIVE_WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private TextWhitespaceNormalizer() {}

    /**
     * @param input raw string; {@code null} is returned as {@code null} (validation is callers' concern)
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.strip();
        s = s.replace('\u3000', ' ');
        return CONSECUTIVE_WHITESPACE.matcher(s).replaceAll(" ");
    }
}

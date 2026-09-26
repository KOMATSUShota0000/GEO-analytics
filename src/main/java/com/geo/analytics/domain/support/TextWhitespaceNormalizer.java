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
        //前後の空白削ってる。
        String s = input.strip();
        //この\u3000は全角スペースをあらわす。半角スペースに置換してる。
        s = s.replace('\u3000', ' ');
        //連続する空白を1つの半角スペースに置換してる。
        return CONSECUTIVE_WHITESPACE.matcher(s).replaceAll(" ");
    }
}

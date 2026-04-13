package com.geo.analytics.domain.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextWhitespaceNormalizerTest {

    @Test
    void null_returns_null() {
        assertThat(TextWhitespaceNormalizer.normalize(null)).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @MethodSource("cases")
    void normalize_cases(String input, String expected) {
        assertThat(TextWhitespaceNormalizer.normalize(input)).isEqualTo(expected);
    }

    private static Stream<Arguments> cases() {
        char ideo = '\u3000';
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("   ", ""),
                Arguments.of("  a  ", "a"),
                Arguments.of("foo   bar", "foo bar"),
                Arguments.of("foo" + ideo + ideo + "bar", "foo bar"),
                Arguments.of(ideo + "a" + ideo, "a"),
                Arguments.of("  a " + ideo + " b  ", "a b"),
                Arguments.of("a\t\tb", "a b"),
                Arguments.of("a \t " + ideo + " b", "a b"),
                Arguments.of("  foo\u00A0\u00A0bar  ", "foo bar"),
                Arguments.of("no-change", "no-change"),
                Arguments.of("x", "x"));
    }

    @Test
    void idempotent_on_already_normalized() {
        String once = TextWhitespaceNormalizer.normalize("  a  b  ");
        assertThat(TextWhitespaceNormalizer.normalize(once)).isEqualTo(once);
    }
}

package com.geo.analytics.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptInjectionValidatorTest {

    @Test
    void parseSafe_acceptsSafeFirstToken() {
        assertThat(PromptInjectionValidator.parseSafe("SAFE")).isTrue();
        assertThat(PromptInjectionValidator.parseSafe("safe\n")).isTrue();
        assertThat(PromptInjectionValidator.parseSafe("SAFE extra")).isTrue();
    }

    @Test
    void parseSafe_rejectsUnsafeAndAmbiguous() {
        assertThat(PromptInjectionValidator.parseSafe("UNSAFE")).isFalse();
        assertThat(PromptInjectionValidator.parseSafe("unsafe")).isFalse();
        assertThat(PromptInjectionValidator.parseSafe("")).isFalse();
        assertThat(PromptInjectionValidator.parseSafe("MAYBE")).isFalse();
        assertThat(PromptInjectionValidator.parseSafe(null)).isFalse();
    }
}

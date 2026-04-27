package com.geo.analytics.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DebatePersonaSystemPromptsTest {

    @Test
    void forDirectorWithScoreInjectionContainsNumericContext() {
        String s = DebatePersonaSystemPrompts.forDirectorWithScoreInjection(0.12, 0.88);
        assertThat(s).contains("0.120000");
        assertThat(s).contains("0.880000");
        assertThat(s).contains("目利き");
    }
}

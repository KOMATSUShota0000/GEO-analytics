package com.geo.analytics.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.enums.IndustryType;
import org.junit.jupiter.api.Test;

class DebatePersonaSystemPromptsTest {

    @Test
    void forDirectorWithScoreInjectionContainsNumericContext() {
        String s = DebatePersonaSystemPrompts.forDirectorWithScoreInjection(0.12, 0.88);
        assertThat(s).contains("0.120000");
        assertThat(s).contains("0.880000");
        assertThat(s).contains("目利き");
    }

    @Test
    void geoFacadeBlock_containsGeoNativeFraming() {
        String block = DebatePersonaSystemPrompts.geoFacadeBlock();
        assertThat(block).contains("GEO Facade");
        assertThat(block).contains("AI可視性ランク");
        assertThat(block).contains("AI推奨ポテンシャル");
        assertThat(block).contains("Brand Recommendation");
        assertThat(block).contains("Information Gain");
        assertThat(block).contains("Constraints (English)");
    }

    @Test
    void ymylSkepticPrompt_containsEEAT() {
        String s = DebatePersonaSystemPrompts.forPersona(DebatePersona.SKEPTIC, IndustryType.YMYL);
        assertThat(s).contains("E-E-A-T");
        assertThat(s).contains("YMYL");
    }

    @Test
    void ecAnalystPrompt_containsReturnPolicyContext() {
        String s = DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, IndustryType.EC);
        assertThat(s).contains("EC");
        assertThat(s).contains("返品");
    }

    @Test
    void forPersona_nullIndustry_fallsBackToOtherOverlay() {
        String s = DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, null);
        assertThat(s).contains("業種コンテキスト: 一般");
    }
}

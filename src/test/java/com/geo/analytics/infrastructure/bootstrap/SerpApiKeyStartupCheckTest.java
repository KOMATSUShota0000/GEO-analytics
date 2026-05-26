package com.geo.analytics.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SerpApiKeyStartupCheckTest {

    @Mock private AppProperties appProperties;

    @Test
    void isConfigured_detectsBlankAndNull() {
        assertThat(SerpApiKeyStartupCheck.isConfigured(null)).isFalse();
        assertThat(SerpApiKeyStartupCheck.isConfigured("")).isFalse();
        assertThat(SerpApiKeyStartupCheck.isConfigured("   ")).isFalse();
        assertThat(SerpApiKeyStartupCheck.isConfigured("abcd1234")).isTrue();
    }

    @Test
    void maskKey_keepsOnlyPrefix() {
        assertThat(SerpApiKeyStartupCheck.maskKey("abcdefghijklmnop")).isEqualTo("abcd****");
        assertThat(SerpApiKeyStartupCheck.maskKey("xyz")).isEqualTo("****");
        assertThat(SerpApiKeyStartupCheck.maskKey(null)).isEmpty();
    }

    @Test
    void run_withBlankKey_doesNotThrowAndDoesNotHaltStartup() {
        AppProperties.Serpapi serpapi = new AppProperties.Serpapi();
        serpapi.setApiKey("");
        when(appProperties.getSerpapi()).thenReturn(serpapi);

        SerpApiKeyStartupCheck check = new SerpApiKeyStartupCheck(appProperties);

        assertThatCode(() -> check.run(null)).doesNotThrowAnyException();
    }

    @Test
    void run_withValidKey_doesNotThrow() {
        AppProperties.Serpapi serpapi = new AppProperties.Serpapi();
        serpapi.setApiKey("realkey1234567890");
        when(appProperties.getSerpapi()).thenReturn(serpapi);

        SerpApiKeyStartupCheck check = new SerpApiKeyStartupCheck(appProperties);

        assertThatCode(() -> check.run(null)).doesNotThrowAnyException();
    }
}

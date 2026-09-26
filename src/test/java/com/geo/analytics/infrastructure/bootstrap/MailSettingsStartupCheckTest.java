package com.geo.analytics.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MailSettingsStartupCheckTest {

    private static MailSettingsStartupCheck checkWith(boolean required, MockEnvironment env) {
        AppProperties props = new AppProperties();
        props.getMail().setRequired(required);
        return new MailSettingsStartupCheck(props, env);
    }

    @Test
    void requiredAndHostMissing_stopsStartup() {
        assertThatThrownBy(() -> checkWith(true, new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_HOST");
    }

    @Test
    void requiredAndHostBlank_stopsStartup() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.mail.host", "  ");

        assertThatThrownBy(() -> checkWith(true, env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiredAndHostSet_starts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.mail.host", "localhost")
                .withProperty("spring.mail.port", "1025");

        assertThatCode(() -> checkWith(true, env).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void notRequired_startsWithoutHost() {
        assertThatCode(() -> checkWith(false, new MockEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void requiredByDefault() {
        assertThat(new AppProperties().getMail().isRequired()).isTrue();
    }
}

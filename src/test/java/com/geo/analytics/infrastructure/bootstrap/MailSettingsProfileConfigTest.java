package com.geo.analytics.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.infrastructure.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 実際の application-dev.yml / application-prod.yml を読み込み、送信先の既定・切り替え・起動停止が
 * Spring の設定として効くことを確かめる。起動中の開発サーバーを止めずに、プロファイル設定の誤りを検出するため。
 */
class MailSettingsProfileConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import(MailSettingsStartupCheck.class)
    static class Config {}

    private static ApplicationContextRunner runnerWith(String yaml) {
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader().load(yaml, new ClassPathResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(Config.class)
                .withInitializer(ctx -> sources.forEach(ctx.getEnvironment().getPropertySources()::addLast))
                // application-prod.yml の app.security.jwt.secret は既定値なしのため、束縛できるよう与える
                .withPropertyValues("JWT_SECRET=test-secret");
    }

    @Test
    void dev_withoutMailSettings_sendsToMailpit() {
        runnerWith("application-dev.yml").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            JavaMailSenderImpl sender = ctx.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getHost()).isEqualTo("localhost");
            assertThat(sender.getPort()).isEqualTo(1025);
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "10000")
                    .containsEntry("mail.smtp.timeout", "10000")
                    .containsEntry("mail.smtp.writetimeout", "10000")
                    .doesNotContainKey("mail.smtp.starttls.required");
            assertThat(ctx.getBean(AppProperties.class).getBootstrap().getEmail()).isEqualTo("bootstrap@example.com");
        });
    }

    @Test
    void dev_withGmailSettings_switchesDestinationAndSeedUser() {
        runnerWith("application-dev.yml")
                .withPropertyValues(
                        "MAIL_HOST=smtp.gmail.com",
                        "MAIL_PORT=587",
                        "MAIL_USERNAME=me@gmail.com",
                        "MAIL_PASSWORD=app-password",
                        "APP_BOOTSTRAP_EMAIL=me+dev@gmail.com")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    JavaMailSenderImpl sender = ctx.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
                    assertThat(sender.getPort()).isEqualTo(587);
                    assertThat(sender.getUsername()).isEqualTo("me@gmail.com");
                    assertThat(ctx.getBean(AppProperties.class).getBootstrap().getEmail())
                            .isEqualTo("me+dev@gmail.com");
                });
    }

    @Test
    void dev_withBlankMailHost_stopsStartup() {
        runnerWith("application-dev.yml").withPropertyValues("MAIL_HOST=").run(ctx -> assertThat(ctx)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_HOST"));
    }

    @Test
    void prod_withoutMailSettings_stopsStartup() {
        runnerWith("application-prod.yml").run(ctx -> assertThat(ctx)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_HOST"));
    }

    @Test
    void prod_withMailSettings_requiresStartTls() {
        runnerWith("application-prod.yml")
                .withPropertyValues("MAIL_HOST=smtp.example.com", "MAIL_USERNAME=user", "MAIL_PASSWORD=secret")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    JavaMailSenderImpl sender = ctx.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("smtp.example.com");
                    assertThat(sender.getPort()).isEqualTo(587);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.starttls.required", "true");
                    assertThat(ctx.getBean(AppProperties.class).getBootstrap().getEmail())
                            .isEqualTo("bootstrap@example.com");
                });
    }
}

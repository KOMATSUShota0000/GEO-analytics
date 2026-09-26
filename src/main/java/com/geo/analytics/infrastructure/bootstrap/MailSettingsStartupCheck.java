package com.geo.analytics.infrastructure.bootstrap;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * メール送信の設定が無いまま起動させない。
 *
 * <p>ログインコードはメールでしか届かないため、設定漏れは「誰もログインできない」に直結する。
 * 既存の監査完了通知のように送信の仕組みが無いと黙って送信を飛ばす作りでは、誰も気づけない。
 */
@Component
public class MailSettingsStartupCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MailSettingsStartupCheck.class);

    static final String HOST_PROPERTY = "spring.mail.host";
    static final String PORT_PROPERTY = "spring.mail.port";

    private final AppProperties appProperties;
    private final Environment environment;

    public MailSettingsStartupCheck(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    // Why: ApplicationRunner だと Web サーバーが受付を始めた後に落ちる。Bean 生成時に検査して受付前に止める。
    @Override
    public void afterPropertiesSet() {
        String host = environment.getProperty(HOST_PROPERTY);
        verify(appProperties.getMail().isRequired(), host);
        if (isConfigured(host)) {
            log.info("メール送信先: {}:{}", host.trim(), environment.getProperty(PORT_PROPERTY, "(既定)"));
        }
    }

    static void verify(boolean required, String host) {
        if (!required || isConfigured(host)) {
            return;
        }
        throw new IllegalStateException(
                "メール送信の設定がありません（spring.mail.host が空）。ログインコードを送れないため起動を止めます。"
                        + " 本番: 環境変数 MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD を設定してください（docs/DEPLOYMENT_ENV.md）。"
                        + " 開発: .env に空の「MAIL_HOST=」があれば行ごと消すと Mailpit へ送ります（docs/DEVELOPMENT_SETUP.md）。");
    }

    static boolean isConfigured(String host) {
        return host != null && !host.isBlank();
    }
}

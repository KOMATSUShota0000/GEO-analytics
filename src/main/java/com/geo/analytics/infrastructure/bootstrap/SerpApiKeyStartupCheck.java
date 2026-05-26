package com.geo.analytics.infrastructure.bootstrap;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 起動時に SERPAPI キーの設定状況を可視化する。
 *
 * <p>未設定でも例外は投げず起動を止めない（プレースホルダ降格は既存設計）。
 * 目的は「本番でキー未設定に誰も気づかず競合データ無しで運用される事故」の防止。
 */
@Component
@Order(1)
public class SerpApiKeyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SerpApiKeyStartupCheck.class);

    private final AppProperties appProperties;

    public SerpApiKeyStartupCheck(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String apiKey = appProperties.getSerpapi() != null
                ? appProperties.getSerpapi().getApiKey()
                : null;
        if (isConfigured(apiKey)) {
            log.info(
                    "SERPAPI key configured (masked={}). 競合エビデンス実取得が有効です。",
                    maskKey(apiKey));
            return;
        }
        log.warn(
                "SERPAPI_API_KEY が未設定です。競合エビデンスは取得されずプレースホルダに降格します"
                        + "（4人ペルソナ議論・相対評価の品質が低下）。"
                        + "本番では環境変数 SERPAPI_API_KEY を設定してください。");
    }

    static boolean isConfigured(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    /** ログ漏洩防止のため先頭4文字のみ残しマスクする。 */
    static String maskKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****";
    }
}

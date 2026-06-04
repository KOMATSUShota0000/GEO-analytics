package com.geo.analytics.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe セルフサーブ自動決済（案B）の設定。
 * secret-key / webhook-secret は機密のため必ず環境変数で注入する（application.yml にハードコード禁止）。
 */
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {
    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "";
    private String cancelUrl = "";
    private final Prices prices = new Prices();

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey != null ? secretKey : "";
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret != null ? webhookSecret : "";
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl != null ? successUrl : "";
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl != null ? cancelUrl : "";
    }

    public Prices getPrices() {
        return prices;
    }

    /** プラン別の Stripe 価格ID（テストモードでは機密ではない）。 */
    public static class Prices {
        private String standard = "";
        private String pro = "";
        private String expert = "";

        public String getStandard() {
            return standard;
        }

        public void setStandard(String standard) {
            this.standard = standard != null ? standard : "";
        }

        public String getPro() {
            return pro;
        }

        public void setPro(String pro) {
            this.pro = pro != null ? pro : "";
        }

        public String getExpert() {
            return expert;
        }

        public void setExpert(String expert) {
            this.expert = expert != null ? expert : "";
        }
    }
}

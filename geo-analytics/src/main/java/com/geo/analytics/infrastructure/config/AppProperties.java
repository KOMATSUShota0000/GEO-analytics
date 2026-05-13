package com.geo.analytics.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Crawler crawler = new Crawler();
    private Ai ai = new Ai();
    private Serpapi serpapi = new Serpapi();
    private Places places = new Places();
    private Pdf pdf = new Pdf();
    private Notifications notifications = new Notifications();
    private Security security;
    private Branding branding = new Branding();

    public Crawler getCrawler() {
        return crawler;
    }

    public void setCrawler(Crawler crawler) {
        this.crawler = crawler;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Serpapi getSerpapi() {
        return serpapi;
    }

    public void setSerpapi(Serpapi serpapi) {
        this.serpapi = serpapi;
    }

    public Places getPlaces() {
        return places;
    }

    public void setPlaces(Places places) {
        this.places = places != null ? places : new Places();
    }

    public Pdf getPdf() {
        return pdf;
    }

    public void setPdf(Pdf pdf) {
        this.pdf = pdf;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public void setNotifications(Notifications notifications) {
        this.notifications = notifications;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Branding getBranding() {
        if (branding == null) {
            branding = new Branding();
        }
        return branding;
    }

    public void setBranding(Branding branding) {
        this.branding = branding;
    }

    public static class Crawler {
        private Duration cacheTtl = Duration.ofHours(24);
        private boolean redisEnabled;
        private String redisHost;
        private int redisPort = 6379;

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl != null ? cacheTtl : Duration.ofHours(24);
        }

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }

        public String getRedisHost() {
            return redisHost;
        }

        public void setRedisHost(String redisHost) {
            this.redisHost = redisHost;
        }

        public int getRedisPort() {
            return redisPort;
        }

        public void setRedisPort(int redisPort) {
            this.redisPort = redisPort;
        }
    }

    public static class Ai {
        private int realtimeThreshold = 10;
        private PromptInjectionGuard promptInjectionGuard = new PromptInjectionGuard();
        private Gemini gemini = new Gemini();

        public int getRealtimeThreshold() {
            return realtimeThreshold;
        }

        public void setRealtimeThreshold(int realtimeThreshold) {
            this.realtimeThreshold = realtimeThreshold;
        }

        public PromptInjectionGuard getPromptInjectionGuard() {
            return promptInjectionGuard;
        }

        public void setPromptInjectionGuard(PromptInjectionGuard promptInjectionGuard) {
            this.promptInjectionGuard =
                    promptInjectionGuard != null ? promptInjectionGuard : new PromptInjectionGuard();
        }

        public Gemini getGemini() {
            return gemini;
        }

        public void setGemini(Gemini gemini) {
            this.gemini = gemini;
        }
    }

    /** RAG 由来 competitor XML を軽量モデルで検閲する設定。 */
    public static class PromptInjectionGuard {
        private int timeoutSeconds = 30;
        /** true のときのみ API 失敗時に通過（フェイルオープン)。デフォルト false（フェイルクローズ）。 */
        private boolean failOpenOnError = false;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = Math.max(1, timeoutSeconds);
        }

        public boolean isFailOpenOnError() {
            return failOpenOnError;
        }

        public void setFailOpenOnError(boolean failOpenOnError) {
            this.failOpenOnError = failOpenOnError;
        }
    }

    public static class Gemini {
        private String apiKey;
        private String modelName;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /** AI Overview / GEO可視性計測向けプロバイダ設定（{@code app.serpapi.*} にバインド）。 */
    public static class Serpapi {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Places {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Pdf {
        private String tempDir;

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }
    }

    public static class Notifications {
        private String mailFrom = "noreply@example.com";

        public String getMailFrom() {
            return mailFrom;
        }

        public void setMailFrom(String mailFrom) {
            this.mailFrom = mailFrom;
        }
    }

    public static class Security {
        private int maxSessionsPerUser = 1;
        private Jwt jwt;
        private Rls rls;

        public int getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }

        public void setMaxSessionsPerUser(int maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }

        public Jwt getJwt() {
            if (jwt == null) {
                jwt = new Jwt();
            }
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public Rls getRls() {
            if (rls == null) {
                rls = new Rls();
            }
            return rls;
        }

        public void setRls(Rls rls) {
            this.rls = rls;
        }
    }

    public static class Jwt {
        private String secret;
        private int accessTokenExpirationSec = 900;
        private int refreshTokenExpirationSec = 2592000;
        private boolean cookieSecure;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getAccessTokenExpirationSec() {
            return accessTokenExpirationSec;
        }

        public void setAccessTokenExpirationSec(int accessTokenExpirationSec) {
            this.accessTokenExpirationSec = accessTokenExpirationSec;
        }

        public int getRefreshTokenExpirationSec() {
            return refreshTokenExpirationSec;
        }

        public void setRefreshTokenExpirationSec(int refreshTokenExpirationSec) {
            this.refreshTokenExpirationSec = refreshTokenExpirationSec;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Rls {
        private boolean enabled = true;
        private String postgresSessionParameter = "app.current_tenant";
        private boolean enforceAppi2026 = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPostgresSessionParameter() {
            return postgresSessionParameter;
        }

        public void setPostgresSessionParameter(String postgresSessionParameter) {
            this.postgresSessionParameter = postgresSessionParameter;
        }

        public boolean isEnforceAppi2026() {
            return enforceAppi2026;
        }

        public void setEnforceAppi2026(boolean enforceAppi2026) {
            this.enforceAppi2026 = enforceAppi2026;
        }
    }

    public static class Branding {
        private String storageRoot = "/tmp/geo-analytics/branding";

        public String getStorageRoot() {
            return storageRoot;
        }

        public void setStorageRoot(String storageRoot) {
            this.storageRoot = storageRoot != null ? storageRoot : "/tmp/geo-analytics/branding";
        }
    }
}

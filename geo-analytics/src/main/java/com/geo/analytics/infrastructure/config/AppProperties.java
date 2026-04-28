package com.geo.analytics.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Crawler crawler = new Crawler();
    private Ai ai = new Ai();
    private Serpapi serpapi = new Serpapi();
    private Pdf pdf = new Pdf();
    private Notifications notifications = new Notifications();
    private Security security;
    private Oracle oracle;

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

    public Oracle getOracle() {
        return oracle;
    }

    public void setOracle(Oracle oracle) {
        this.oracle = oracle;
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
        private TokenProfitGuard tokenProfitGuard = new TokenProfitGuard();
        private PromptInjectionGuard promptInjectionGuard = new PromptInjectionGuard();
        private Gemini gemini = new Gemini();
        private Deepseek deepseek = new Deepseek();

        public int getRealtimeThreshold() {
            return realtimeThreshold;
        }

        public void setRealtimeThreshold(int realtimeThreshold) {
            this.realtimeThreshold = realtimeThreshold;
        }

        public TokenProfitGuard getTokenProfitGuard() {
            return tokenProfitGuard;
        }

        public void setTokenProfitGuard(TokenProfitGuard tokenProfitGuard) {
            this.tokenProfitGuard = tokenProfitGuard != null ? tokenProfitGuard : new TokenProfitGuard();
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

        public Deepseek getDeepseek() {
            return deepseek;
        }

        public void setDeepseek(Deepseek deepseek) {
            this.deepseek = deepseek;
        }
    }

    /**
     * オンボーディング競合 XML（RAG）に使える入力文字数の上限を、プラン予算から逆算するための設定。
     */
    public static class TokenProfitGuard {
        /** 確保する利益率（0〜1）。コストに回す割合は {@code 1 - reservedMarginRate}。 */
        private double reservedMarginRate = 0.7d;
        /** 入力トークン単価（USD / 100万トークン）。 */
        private double usdPerMillionTokens = 3.5d;
        /** 1文字あたり概算トークン数（保守側）。許容文字数 = 許容トークン / この値。 */
        private double charsPerTokenApprox = 1.0d;
        /** プランごとの competitor エビデンス予算（USD / 呼び出し）。 */
        private Map<String, Double> planBudgetUsd = new LinkedHashMap<>();

        public double getReservedMarginRate() {
            return reservedMarginRate;
        }

        public void setReservedMarginRate(double reservedMarginRate) {
            this.reservedMarginRate = reservedMarginRate;
        }

        public double getUsdPerMillionTokens() {
            return usdPerMillionTokens;
        }

        public void setUsdPerMillionTokens(double usdPerMillionTokens) {
            this.usdPerMillionTokens = usdPerMillionTokens;
        }

        public double getCharsPerTokenApprox() {
            return charsPerTokenApprox;
        }

        public void setCharsPerTokenApprox(double charsPerTokenApprox) {
            this.charsPerTokenApprox = charsPerTokenApprox;
        }

        public Map<String, Double> getPlanBudgetUsd() {
            return planBudgetUsd;
        }

        public void setPlanBudgetUsd(Map<String, Double> planBudgetUsd) {
            this.planBudgetUsd = planBudgetUsd != null ? planBudgetUsd : new LinkedHashMap<>();
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

    public static class Deepseek {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

    public static class Pdf {
        private String baseUrl;
        private String tempDir;
        private String internalToken;
        private String defaultBrandColor;
        private String defaultLogoUrl;
        private Integer maxConcurrent;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }

        public String getInternalToken() {
            return internalToken;
        }

        public void setInternalToken(String internalToken) {
            this.internalToken = internalToken;
        }

        public String getDefaultBrandColor() {
            return defaultBrandColor;
        }

        public void setDefaultBrandColor(String defaultBrandColor) {
            this.defaultBrandColor = defaultBrandColor;
        }

        public String getDefaultLogoUrl() {
            return defaultLogoUrl;
        }

        public void setDefaultLogoUrl(String defaultLogoUrl) {
            this.defaultLogoUrl = defaultLogoUrl;
        }

        public Integer getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(Integer maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
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
        private String oracleClientIdentifier = "GEO_ANALYTICS_TENANT";
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

        public String getOracleClientIdentifier() {
            return oracleClientIdentifier;
        }

        public void setOracleClientIdentifier(String oracleClientIdentifier) {
            this.oracleClientIdentifier = oracleClientIdentifier;
        }

        public boolean isEnforceAppi2026() {
            return enforceAppi2026;
        }

        public void setEnforceAppi2026(boolean enforceAppi2026) {
            this.enforceAppi2026 = enforceAppi2026;
        }
    }

    public static class Oracle {
        private Datasource datasource;

        public Datasource getDatasource() {
            if (datasource == null) {
                datasource = new Datasource();
            }
            return datasource;
        }

        public void setDatasource(Datasource datasource) {
            this.datasource = datasource;
        }
    }

    public static class Datasource {
        private String url;
        private String username;
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}

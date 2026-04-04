package com.geo.analytics.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
        private Gemini gemini = new Gemini();
        private Deepseek deepseek = new Deepseek();

        public int getRealtimeThreshold() {
            return realtimeThreshold;
        }

        public void setRealtimeThreshold(int realtimeThreshold) {
            this.realtimeThreshold = realtimeThreshold;
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
        private Rls rls;

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

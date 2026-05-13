package com.geo.analytics.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.infrastructure.crawler.CachedWebCrawlerDecorator;
import com.geo.analytics.infrastructure.crawler.JsoupWebCrawlerAdapter;
import com.geo.analytics.infrastructure.crawler.TieredCrawlCache;
import com.geo.analytics.infrastructure.crawler.safety.PerDomainRequestLimiter;
import com.geo.analytics.infrastructure.crawler.safety.SafeHttpClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CrawlerConfiguration {
    @Bean
    PerDomainRequestLimiter perDomainRequestLimiter() {
        return new PerDomainRequestLimiter();
    }

    @Bean
    SafeHttpClient safeHttpClient(PerDomainRequestLimiter perDomainRequestLimiter) {
        return new SafeHttpClient(perDomainRequestLimiter);
    }

    @Configuration
    @ConditionalOnProperty(name = "app.crawler.redis-enabled", havingValue = "true")
    static class CrawlerRedisConfiguration {
        @Bean(destroyMethod = "shutdown")
        RedisClient crawlRedisClient(AppProperties appProperties) {
            AppProperties.Crawler crawler = appProperties.getCrawler();
            String host = crawler.getRedisHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("app.crawler.redis-host is required when redis-enabled=true");
            }
            RedisURI redisUri = RedisURI.builder()
                .withHost(host.strip())
                .withPort(crawler.getRedisPort())
                .build();
            return RedisClient.create(redisUri);
        }

        @Bean(destroyMethod = "close")
        StatefulRedisConnection<String, String> crawlRedisConnection(RedisClient crawlRedisClient) {
            return crawlRedisClient.connect();
        }
    }

    @Bean
    TieredCrawlCache tieredCrawlCache(ObjectProvider<StatefulRedisConnection<String, String>> redisConnectionProvider) {
        return new TieredCrawlCache(redisConnectionProvider.getIfAvailable());
    }

    @Bean
    @Primary
    WebCrawlerPort webCrawlerPort(
            ObjectMapper objectMapper,
            AppProperties appProperties,
            TieredCrawlCache tieredCrawlCache) {
        long ttlSeconds = Math.max(1L, appProperties.getCrawler().getCacheTtl().toSeconds());
        return new CachedWebCrawlerDecorator(
            new JsoupWebCrawlerAdapter(),
            objectMapper,
            tieredCrawlCache,
            ttlSeconds);
    }
}

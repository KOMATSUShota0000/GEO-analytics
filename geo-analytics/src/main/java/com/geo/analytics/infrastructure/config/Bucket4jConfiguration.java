package com.geo.analytics.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Bucket4jConfiguration {

    public static final String PLAN_QUOTA_CAFFEINE_PROXY_MANAGER = "planQuotaCaffeineProxyManager";

    @Bean("rateLimitProxyManager")
    public ProxyManager<String> rateLimitProxyManager() {
        @SuppressWarnings("unchecked")
        Caffeine<String, RemoteBucketState> caffeine =
                (Caffeine<String, RemoteBucketState>) (Caffeine<?, ?>) Caffeine.newBuilder().maximumSize(10000L);
        return new CaffeineProxyManager<>(caffeine, Duration.ofDays(1L));
    }

    @Bean(PLAN_QUOTA_CAFFEINE_PROXY_MANAGER)
    public CaffeineProxyManager<String> planQuotaCaffeineProxyManager() {
        @SuppressWarnings("unchecked")
        Caffeine<String, RemoteBucketState> caffeine =
                (Caffeine<String, RemoteBucketState>) (Caffeine<?, ?>) Caffeine.newBuilder()
                        .maximumSize(10000L);
        return new CaffeineProxyManager<>(caffeine, Duration.ofDays(2L));
    }

    @Bean("planQuotaProxyManager")
    public ProxyManager<String> planQuotaProxyManager(
            @Qualifier(PLAN_QUOTA_CAFFEINE_PROXY_MANAGER) CaffeineProxyManager<String> planQuotaCaffeineProxyManager) {
        return planQuotaCaffeineProxyManager;
    }
}

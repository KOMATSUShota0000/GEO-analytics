package com.geo.analytics.infrastructure.config;

import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    /** キーは {@code sessionId}。値は有効セッションの印（常に {@code true}）のみを格納する。 */
    @Bean
    public Cache<UUID, Boolean> userSessionsCache() {
        return Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
    }

    @Bean
    public Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache() {
        return Caffeine.newBuilder().expireAfterWrite(CACHE_TTL).build();
    }
}

package com.geo.analytics.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = CacheConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CacheConfigTest.MinimalCacheManagerConfig.class)
class CacheConfigTest {

    @Configuration
    static class MinimalCacheManagerConfig {
        @Bean
        CacheManager cacheManager() {
            return new CaffeineCacheManager();
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Cache<UUID, Boolean> userSessionsCache;

    @Autowired
    private Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;

    @Test
    void userSessionsCacheAndOrgTenantAffiliationCacheAreRegisteredAndInjectable() {
        assertThat(applicationContext.containsBean("userSessionsCache")).isTrue();
        assertThat(applicationContext.containsBean("orgTenantAffiliationCache")).isTrue();
        assertThat(applicationContext.getBean("userSessionsCache")).isSameAs(userSessionsCache);
        assertThat(applicationContext.getBean("orgTenantAffiliationCache")).isSameAs(orgTenantAffiliationCache);
        assertThat(userSessionsCache).isNotNull();
        assertThat(orgTenantAffiliationCache).isNotNull();
    }

    @Test
    void userSessionsCacheStoresAndRetrievesSessionValidity() {
        UUID sessionId = UUID.fromString("11111111-2222-4333-8444-555555555555");

        userSessionsCache.put(sessionId, Boolean.TRUE);
        assertThat(userSessionsCache.getIfPresent(sessionId)).isTrue();
        assertThat(userSessionsCache.getIfPresent(UUID.fromString("99999999-9999-4999-8999-999999999999")))
                .isNull();
    }

    @Test
    void orgTenantAffiliationCacheStoresAndRetrievesBooleanValues() {
        UUID orgId = UUID.fromString("aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee");
        UUID tenantId = UUID.fromString("bbbbbbbb-cccc-4ddd-eeee-ffffffffffff");
        OrgTenantKey key = new OrgTenantKey(orgId, tenantId);

        orgTenantAffiliationCache.put(key, Boolean.TRUE);
        assertThat(orgTenantAffiliationCache.getIfPresent(key)).isTrue();
    }
}

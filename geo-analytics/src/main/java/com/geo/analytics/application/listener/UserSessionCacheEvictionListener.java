package com.geo.analytics.application.listener;

import com.geo.analytics.application.event.UserSessionEvictedEvent;
import com.geo.analytics.infrastructure.tenant.OrgTenantKey;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserSessionCacheEvictionListener {

    private final Cache<UUID, UUID> userSessionsCache;
    private final Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache;

    public UserSessionCacheEvictionListener(
            @Qualifier("userSessionsCache") Cache<UUID, UUID> userSessionsCache,
            @Qualifier("orgTenantAffiliationCache") Cache<OrgTenantKey, Boolean> orgTenantAffiliationCache) {
        this.userSessionsCache = userSessionsCache;
        this.orgTenantAffiliationCache = orgTenantAffiliationCache;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSessionEvicted(UserSessionEvictedEvent event) {
        userSessionsCache.invalidate(event.userId());
        UUID targetOrgId = event.organizationId();
        orgTenantAffiliationCache
                .asMap()
                .keySet()
                .removeIf(key -> key.orgId().equals(targetOrgId));
    }
}
